package com.jacobcraig.debtmngr.service

import com.jacobcraig.debtmngr.domain.*
import com.jacobcraig.debtmngr.repository.EntryRepository
import com.jacobcraig.debtmngr.repository.GroupRepository
import com.jacobcraig.debtmngr.repository.ParticipantRepository
import com.jacobcraig.debtmngr.repository.TransactionRepository
import jakarta.persistence.EntityNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class TransactionService(
    private val groupRepository: GroupRepository,
    private val participantRepository: ParticipantRepository,
    private val transactionRepository: TransactionRepository,
    private val entryRepository: EntryRepository
) {

    @JvmOverloads
    fun createExpense(
        groupId: Long,
        payerId: Long,
        amount: Long,
        description: String,
        consumerIds: List<Long>,
        date: Instant? = null
    ): Transaction {
        require(description.isNotBlank()) { "Expense description cannot be blank" }
        require(amount > 0) { "Expense amount must be positive" }
        require(consumerIds.isNotEmpty()) { "At least one consumer must be selected" }

        val group = groupRepository.findById(groupId)
            .orElseThrow { EntityNotFoundException("Group not found with id: $groupId") }

        val payer = participantRepository.findById(payerId)
            .orElseThrow { EntityNotFoundException("Participant not found with id: $payerId") }

        require(payer.group.id == groupId) { "Payer does not belong to group $groupId" }

        val distinctConsumerIds = consumerIds.distinct()
        val consumers = distinctConsumerIds.map { consumerId ->
            val consumer = participantRepository.findById(consumerId)
                .orElseThrow { EntityNotFoundException("Participant not found with id: $consumerId") }
            require(consumer.group.id == groupId) { "Consumer $consumerId does not belong to group $groupId" }
            consumer
        }

        val consumerMap = consumers.associateBy { checkNotNull(it.id) }

        val shares = EqualSplitCalculator.calculate(
            totalAmount = amount,
            payerId = payerId,
            consumerIds = distinctConsumerIds
        )

        val transaction = Transaction(
            group = group,
            description = description.trim(),
            amount = amount,
            payer = payer,
            type = TransactionType.EXPENSE,
            createdAt = date ?: Instant.now()
        )

        // Payer CREDIT entry for full amount
        val creditEntry = Entry(
            transaction = transaction,
            account = payer.account,
            type = EntryType.CREDIT,
            amount = amount
        )
        transaction.addEntry(creditEntry)

        // Consumer DEBIT entries for each share
        for (share in shares) {
            if (share.amount > 0) {
                val consumer = consumerMap.getValue(share.participantId)
                val debitEntry = Entry(
                    transaction = transaction,
                    account = consumer.account,
                    type = EntryType.DEBIT,
                    amount = share.amount
                )
                transaction.addEntry(debitEntry)
            }
        }

        // Strictly enforce double-entry invariant and verify debits and credits equal amount
        val totalDebits = transaction.totalDebits()
        val totalCredits = transaction.totalCredits()
        check(totalDebits == amount && totalCredits == amount) {
            "Total debits ($totalDebits) and credits ($totalCredits) must equal expense amount ($amount)"
        }
        transaction.validateDoubleEntry()

        return transactionRepository.save(transaction)
    }

    @Transactional(readOnly = true)
    fun getTransactionsForGroup(groupId: Long): List<Transaction> {
        if (!groupRepository.existsById(groupId)) {
            throw EntityNotFoundException("Group not found with id: $groupId")
        }
        return transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(groupId)
    }

    @Transactional(readOnly = true)
    fun getParticipantBalances(groupId: Long): Map<Long, Long> {
        if (!groupRepository.existsById(groupId)) {
            throw EntityNotFoundException("Group not found with id: $groupId")
        }
        val participants = participantRepository.findByGroupIdOrderByIdAsc(groupId)
        val activeEntries = entryRepository.findActiveByGroupId(groupId)

        val entriesByAccountId = activeEntries.groupBy { it.account.id }

        return participants.associate { participant ->
            val participantId = checkNotNull(participant.id)
            val accountId = participant.account.id
            val entries = entriesByAccountId[accountId] ?: emptyList()

            val credits = entries.filter { it.type == EntryType.CREDIT }.sumOf { it.amount }
            val debits = entries.filter { it.type == EntryType.DEBIT }.sumOf { it.amount }
            val balance = credits - debits

            participantId to balance
        }
    }
}
