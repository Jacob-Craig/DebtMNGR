package com.jacobcraig.debtmngr.service

import com.jacobcraig.debtmngr.domain.*
import com.jacobcraig.debtmngr.repository.AuditLogRepository
import com.jacobcraig.debtmngr.repository.CategoryRepository
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
    private val entryRepository: EntryRepository,
    private val categoryRepository: CategoryRepository,
    private val auditLogRepository: AuditLogRepository
) {

    @JvmOverloads
    fun createExpense(
        groupId: Long,
        payerId: Long,
        amount: Long,
        description: String,
        consumerIds: List<Long> = emptyList(),
        date: Instant? = null,
        splitMode: SplitMode = SplitMode.EQUAL,
        exactAmounts: Map<Long, Long> = emptyMap(),
        categoryId: Long? = null
    ): Transaction {
        require(description.isNotBlank()) { "Expense description cannot be blank" }
        require(amount > 0) { "Expense amount must be positive" }

        val group = groupRepository.findById(groupId)
            .orElseThrow { EntityNotFoundException("Group not found with id: $groupId") }

        val payer = participantRepository.findById(payerId)
            .orElseThrow { EntityNotFoundException("Participant not found with id: $payerId") }

        require(payer.group.id == groupId) { "Payer does not belong to group $groupId" }

        val (shares, participantMap) = when (splitMode) {
            SplitMode.EQUAL -> {
                require(consumerIds.isNotEmpty()) { "At least one consumer must be selected" }
                val distinctConsumerIds = consumerIds.distinct()
                val participants = loadGroupParticipants(groupId, distinctConsumerIds, "Consumer")
                val shares = EqualSplitCalculator.calculate(
                    totalAmount = amount,
                    payerId = payerId,
                    consumerIds = distinctConsumerIds
                )
                shares to participants
            }
            SplitMode.EXACT -> {
                require(exactAmounts.isNotEmpty()) { "At least one consumer must be assigned an amount" }
                val participants = loadGroupParticipants(groupId, exactAmounts.keys, "Participant")
                val shares = ExactSplitCalculator.calculate(
                    totalAmount = amount,
                    exactAmounts = exactAmounts
                )
                shares to participants
            }
        }

        val category = categoryId?.let { catId ->
            val cat = categoryRepository.findById(catId)
                .orElseThrow { EntityNotFoundException("Category not found with id: $catId") }
            require(cat.isAvailableIn(group)) {
                "Category ${cat.name} does not belong to group $groupId"
            }
            cat
        }

        val transaction = Transaction(
            group = group,
            description = description.trim(),
            amount = amount,
            payer = payer,
            type = TransactionType.EXPENSE,
            category = category,
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
                val consumer = participantMap.getValue(share.participantId)
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

    @JvmOverloads
    fun createSettlement(
        groupId: Long,
        payerId: Long,
        receiverId: Long,
        amount: Long,
        date: Instant? = null,
        notes: String? = null
    ): Transaction {
        require(amount > 0) { "Settlement amount must be positive" }
        require(payerId != receiverId) { "Payer and receiver cannot be the same participant" }

        val group = groupRepository.findById(groupId)
            .orElseThrow { EntityNotFoundException("Group not found with id: $groupId") }

        val payer = participantRepository.findById(payerId)
            .orElseThrow { EntityNotFoundException("Participant not found with id: $payerId") }
        require(payer.group.id == groupId) { "Payer does not belong to group $groupId" }

        val receiver = participantRepository.findById(receiverId)
            .orElseThrow { EntityNotFoundException("Participant not found with id: $receiverId") }
        require(receiver.group.id == groupId) { "Receiver does not belong to group $groupId" }

        val payerAccountId = checkNotNull(payer.account.id) { "Payer account must be initialized" }
        val receiverAccountId = checkNotNull(receiver.account.id) { "Receiver account must be initialized" }

        val description = if (!notes.isNullOrBlank()) {
            "Settlement: ${payer.name} paid ${receiver.name} - ${notes.trim()}"
        } else {
            "Settlement: ${payer.name} paid ${receiver.name}"
        }

        val settlement = Transaction(
            group = group,
            description = description,
            amount = amount,
            payer = payer,
            type = TransactionType.SETTLEMENT,
            createdAt = date ?: Instant.now()
        )

        val creditEntry = Entry(
            transaction = settlement,
            account = payer.account,
            type = EntryType.CREDIT,
            amount = amount
        )
        settlement.addEntry(creditEntry)

        val debitEntry = Entry(
            transaction = settlement,
            account = receiver.account,
            type = EntryType.DEBIT,
            amount = amount
        )
        settlement.addEntry(debitEntry)

        val totalDebits = settlement.totalDebits()
        val totalCredits = settlement.totalCredits()
        check(totalDebits == amount && totalCredits == amount) {
            "Total debits ($totalDebits) and credits ($totalCredits) must equal settlement amount ($amount)"
        }
        settlement.validateDoubleEntry()

        val savedSettlement = transactionRepository.save(settlement)

        val priorTransactions = transactionRepository.findUnlockedTransactionsBetweenAccounts(
            groupId = groupId,
            account1Id = payerAccountId,
            account2Id = receiverAccountId,
            beforeInstant = savedSettlement.createdAt,
            excludeId = savedSettlement.id ?: -1L
        )
        for (priorTx in priorTransactions) {
            priorTx.lock()
            transactionRepository.save(priorTx)
        }

        return savedSettlement
    }

    @Transactional(readOnly = true)
    fun getTransactionsForGroup(groupId: Long, categoryId: Long? = null): List<Transaction> {
        if (!groupRepository.existsById(groupId)) {
            throw EntityNotFoundException("Group not found with id: $groupId")
        }
        return if (categoryId != null) {
            transactionRepository.findByGroupIdAndCategoryIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(groupId, categoryId)
        } else {
            transactionRepository.findByGroupIdAndIsDeletedFalseOrderByCreatedAtDescIdDesc(groupId)
        }
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

    @Transactional(readOnly = true)
    fun getTransaction(id: Long): Transaction {
        return transactionRepository.findById(id)
            .orElseThrow { EntityNotFoundException("Transaction not found with id: $id") }
    }

    @Transactional(readOnly = true)
    fun getAuditLogs(transactionId: Long): List<AuditLog> {
        return auditLogRepository.findByTransactionIdOrderByCreatedAtAscIdAsc(transactionId)
    }

    @Transactional(readOnly = true)
    fun getAdjustments(originalTransactionId: Long): List<Transaction> {
        return transactionRepository.findByOriginalTransactionIdAndIsDeletedFalseOrderByCreatedAtAscIdAsc(originalTransactionId)
    }

    fun deleteTransaction(transactionId: Long, reason: String? = null): Transaction {
        val transaction = transactionRepository.findById(transactionId)
            .orElseThrow { EntityNotFoundException("Transaction not found with id: $transactionId") }
        check(!transaction.isLocked) { "Cannot delete a locked transaction" }
        check(!transaction.isDeleted) { "Transaction is already deleted" }

        val priorState = serializeTransactionState(transaction)
        transaction.softDelete()
        val saved = transactionRepository.save(transaction)

        val auditLog = AuditLog(
            transaction = transaction,
            action = AuditAction.DELETE,
            serializedPriorState = priorState,
            reason = reason?.trim()?.ifBlank { null }
        )
        auditLogRepository.save(auditLog)

        return saved
    }

    @JvmOverloads
    fun editExpense(
        transactionId: Long,
        description: String,
        amount: Long,
        payerId: Long,
        consumerIds: List<Long> = emptyList(),
        date: Instant? = null,
        splitMode: SplitMode = SplitMode.EQUAL,
        exactAmounts: Map<Long, Long> = emptyMap(),
        categoryId: Long? = null,
        reason: String? = null
    ): Transaction {
        val oldTx = transactionRepository.findById(transactionId)
            .orElseThrow { EntityNotFoundException("Transaction not found with id: $transactionId") }

        check(!oldTx.isLocked) { "Cannot edit a locked transaction" }
        check(!oldTx.isDeleted) { "Cannot edit a deleted transaction" }
        require(description.isNotBlank()) { "Expense description cannot be blank" }
        require(amount > 0) { "Expense amount must be positive" }

        val groupId = checkNotNull(oldTx.group.id)
        val payer = participantRepository.findById(payerId)
            .orElseThrow { EntityNotFoundException("Participant not found with id: $payerId") }
        require(payer.group.id == groupId) { "Payer does not belong to group $groupId" }

        val (shares, participantMap) = when (splitMode) {
            SplitMode.EQUAL -> {
                require(consumerIds.isNotEmpty()) { "At least one consumer must be selected" }
                val distinctConsumerIds = consumerIds.distinct()
                val participants = loadGroupParticipants(groupId, distinctConsumerIds, "Consumer")
                val shares = EqualSplitCalculator.calculate(
                    totalAmount = amount,
                    payerId = payerId,
                    consumerIds = distinctConsumerIds
                )
                shares to participants
            }
            SplitMode.EXACT -> {
                require(exactAmounts.isNotEmpty()) { "At least one consumer must be assigned an amount" }
                val participants = loadGroupParticipants(groupId, exactAmounts.keys, "Participant")
                val shares = ExactSplitCalculator.calculate(
                    totalAmount = amount,
                    exactAmounts = exactAmounts
                )
                shares to participants
            }
        }

        val category = categoryId?.let { catId ->
            val cat = categoryRepository.findById(catId)
                .orElseThrow { EntityNotFoundException("Category not found with id: $catId") }
            require(cat.isAvailableIn(oldTx.group)) {
                "Category ${cat.name} does not belong to group $groupId"
            }
            cat
        }

        val priorState = serializeTransactionState(oldTx)
        oldTx.softDelete()
        transactionRepository.save(oldTx)

        val auditLog = AuditLog(
            transaction = oldTx,
            action = AuditAction.EDIT,
            serializedPriorState = priorState,
            reason = reason?.trim()?.ifBlank { null }
        )
        auditLogRepository.save(auditLog)

        val newTransaction = Transaction(
            group = oldTx.group,
            description = description.trim(),
            amount = amount,
            payer = payer,
            type = TransactionType.EXPENSE,
            category = category,
            createdAt = date ?: oldTx.createdAt,
            originalTransaction = oldTx
        )

        val creditEntry = Entry(
            transaction = newTransaction,
            account = payer.account,
            type = EntryType.CREDIT,
            amount = amount
        )
        newTransaction.addEntry(creditEntry)

        for (share in shares) {
            if (share.amount > 0) {
                val consumer = participantMap.getValue(share.participantId)
                val debitEntry = Entry(
                    transaction = newTransaction,
                    account = consumer.account,
                    type = EntryType.DEBIT,
                    amount = share.amount
                )
                newTransaction.addEntry(debitEntry)
            }
        }

        val totalDebits = newTransaction.totalDebits()
        val totalCredits = newTransaction.totalCredits()
        check(totalDebits == amount && totalCredits == amount) {
            "Total debits ($totalDebits) and credits ($totalCredits) must equal expense amount ($amount)"
        }
        newTransaction.validateDoubleEntry()

        return transactionRepository.save(newTransaction)
    }

    @JvmOverloads
    fun createAdjustment(
        originalTransactionId: Long,
        description: String,
        amount: Long,
        payerId: Long,
        consumerIds: List<Long> = emptyList(),
        splitMode: SplitMode = SplitMode.EQUAL,
        exactAmounts: Map<Long, Long> = emptyMap(),
        date: Instant? = null
    ): Transaction {
        val originalTx = transactionRepository.findById(originalTransactionId)
            .orElseThrow { EntityNotFoundException("Transaction not found with id: $originalTransactionId") }

        require(originalTx.isLocked) { "Adjustment can only be made to a locked transaction" }
        require(!originalTx.isDeleted) { "Cannot adjust a deleted transaction" }
        require(description.isNotBlank()) { "Adjustment description cannot be blank" }
        require(amount > 0) { "Adjustment amount must be positive" }

        val groupId = checkNotNull(originalTx.group.id)
        val payer = participantRepository.findById(payerId)
            .orElseThrow { EntityNotFoundException("Participant not found with id: $payerId") }
        require(payer.group.id == groupId) { "Payer does not belong to group $groupId" }

        val (shares, participantMap) = when (splitMode) {
            SplitMode.EQUAL -> {
                require(consumerIds.isNotEmpty()) { "At least one consumer must be selected" }
                val distinctConsumerIds = consumerIds.distinct()
                val participants = loadGroupParticipants(groupId, distinctConsumerIds, "Consumer")
                val shares = EqualSplitCalculator.calculate(
                    totalAmount = amount,
                    payerId = payerId,
                    consumerIds = distinctConsumerIds
                )
                shares to participants
            }
            SplitMode.EXACT -> {
                require(exactAmounts.isNotEmpty()) { "At least one consumer must be assigned an amount" }
                val participants = loadGroupParticipants(groupId, exactAmounts.keys, "Participant")
                val shares = ExactSplitCalculator.calculate(
                    totalAmount = amount,
                    exactAmounts = exactAmounts
                )
                shares to participants
            }
        }

        val adjustment = Transaction(
            group = originalTx.group,
            description = description.trim(),
            amount = amount,
            payer = payer,
            type = TransactionType.ADJUSTMENT,
            category = originalTx.category,
            originalTransaction = originalTx,
            createdAt = date ?: Instant.now()
        )

        val creditEntry = Entry(
            transaction = adjustment,
            account = payer.account,
            type = EntryType.CREDIT,
            amount = amount
        )
        adjustment.addEntry(creditEntry)

        for (share in shares) {
            if (share.amount > 0) {
                val consumer = participantMap.getValue(share.participantId)
                val debitEntry = Entry(
                    transaction = adjustment,
                    account = consumer.account,
                    type = EntryType.DEBIT,
                    amount = share.amount
                )
                adjustment.addEntry(debitEntry)
            }
        }

        val totalDebits = adjustment.totalDebits()
        val totalCredits = adjustment.totalCredits()
        check(totalDebits == amount && totalCredits == amount) {
            "Total debits ($totalDebits) and credits ($totalCredits) must equal adjustment amount ($amount)"
        }
        adjustment.validateDoubleEntry()

        return transactionRepository.save(adjustment)
    }

    private fun serializeTransactionState(transaction: Transaction): String {
        fun escape(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

        val entriesJson = transaction.entries.joinToString(separator = ",", prefix = "[", postfix = "]") { entry ->
            """{"id":${entry.id},"accountId":${entry.account.id},"type":"${entry.type.name}","amount":${entry.amount}}"""
        }

        return buildString {
            append("{")
            append(""""id":${transaction.id},""")
            append(""""description":"${escape(transaction.description)}",""")
            append(""""amount":${transaction.amount},""")
            append(""""payerId":${transaction.payer.id},""")
            append(""""payerName":"${escape(transaction.payer.name)}",""")
            append(""""type":"${transaction.type.name}",""")
            append(""""categoryId":${transaction.category?.id},""")
            append(""""categoryName":${transaction.category?.name?.let { "\"${escape(it)}\"" } ?: "null"},""")
            append(""""isLocked":${transaction.isLocked},""")
            append(""""isDeleted":${transaction.isDeleted},""")
            append(""""createdAt":"${transaction.createdAt}",""")
            append(""""entries":$entriesJson""")
            append("}")
        }
    }

    private fun loadGroupParticipants(
        groupId: Long,
        participantIds: Collection<Long>,
        roleName: String = "Participant"
    ): Map<Long, Participant> {
        return participantIds.associate { participantId ->
            val participant = participantRepository.findById(participantId)
                .orElseThrow { EntityNotFoundException("Participant not found with id: $participantId") }
            require(participant.group.id == groupId) { "$roleName $participantId does not belong to group $groupId" }
            checkNotNull(participant.id) to participant
        }
    }
}
