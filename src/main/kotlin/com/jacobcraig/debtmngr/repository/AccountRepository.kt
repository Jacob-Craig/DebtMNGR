package com.jacobcraig.debtmngr.repository

import com.jacobcraig.debtmngr.domain.Account
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface AccountRepository : JpaRepository<Account, Long>
