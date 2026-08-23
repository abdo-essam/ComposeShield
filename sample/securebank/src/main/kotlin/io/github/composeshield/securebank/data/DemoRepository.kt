@file:Suppress("MagicNumber")

package io.github.composeshield.securebank.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

/** One bank account shown on the overview screen. */
data class Account(
    val id: String,
    val name: String,
    val maskedNumber: String,
    val balanceMajor: Long,
    val balanceMinor: Int,
) {
    fun formatted(): String = "$%,d.%02d".format(balanceMajor, balanceMinor)
}

/** A posted transaction on the transactions screen. */
data class Transaction(
    val label: String,
    val amountMinor: Long,
) {
    /** Signed display string; positive amounts are credits. */
    fun formatted(): String {
        val sign = if (amountMinor < 0) "-" else "+"
        val absCents = kotlin.math.abs(amountMinor)
        return "$sign$%,d.%02d".format(absCents / 100, absCents % 100)
    }
}

/** Static demo data. A real integration would stream this from a backend — irrelevant to the library. */
object DemoRepository {
    const val CARD_NUMBER = "4111 1111 1111 1111"
    const val CARD_EXPIRY = "09/29"
    const val CARD_CVV = "123"
    const val CARD_HOLDER = "A. ESSAM"
    const val CARD_LAST4 = "4291"

    private val _accounts: SnapshotStateList<Account> =
        mutableStateListOf(
            Account("ACC-001", "Everyday Checking", "•• 4821", 4_812L, 90),
            Account("ACC-002", "High-Interest Savings", "•• 7734", 18_250L, 0),
        )

    val accounts: List<Account> get() = _accounts.toList()

    val totalBalance: String
        get() =
            "$%,d.%02d".format(
                accounts.sumOf { it.balanceMajor },
                accounts.sumOf { it.balanceMinor },
            )

    private val _transactions: SnapshotStateList<Transaction> =
        mutableStateListOf(
            Transaction("Salary deposit", 315_000),
            Transaction("Transfer to Savings", -50_000),
            Transaction("Groceries", -8_214),
            Transaction("Coffee Corner", -460),
            Transaction("Marketplace refund", 1_999),
            Transaction("Utilities", -12_740),
        )

    val transactions: List<Transaction> get() = _transactions.toList()
}
