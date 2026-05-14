package com.tuapp.fintrack.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.data.model.PayEvent
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ExportTransactionsUseCaseTest {

    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var payEventDao: FakePayEventDao
    private lateinit var categoryDao: FakeCategoryDao
    private lateinit var useCase: ExportTransactionsUseCase

    private val now = System.currentTimeMillis()

    @Before
    fun setUp() {
        transactionDao = FakeTransactionDao()
        payEventDao = FakePayEventDao()
        categoryDao = FakeCategoryDao()
        useCase = ExportTransactionsUseCase(transactionDao, payEventDao, categoryDao)
    }

    @Test
    fun `returns empty list when no data`() = runTest {
        val result = useCase()
        assertThat(result).isEmpty()
    }

    @Test
    fun `real transactions are included and denormalized`() = runTest {
        val cat = Category(
            id = 1L, name = "Groceries",
            applicability = CategoryApplicability.EXPENSE,
            colorHex = "#FF0000", createdAt = now
        )
        categoryDao.setCategories(listOf(cat))
        transactionDao.setTransactions(
            listOf(
                Transaction(
                    id = 1L, type = TransactionType.EXPENSE,
                    amountCents = 1234L, categoryId = 1L,
                    description = "Weekly shopping", occurredAt = now, createdAt = now
                )
            )
        )

        val result = useCase()

        assertThat(result).hasSize(1)
        assertThat(result[0].categoryName).isEqualTo("Groceries")
        assertThat(result[0].amountCents).isEqualTo(1234L)
        assertThat(result[0].isPayEvent).isFalse()
    }

    @Test
    fun `transaction with no category gets null categoryName`() = runTest {
        transactionDao.setTransactions(
            listOf(
                Transaction(
                    id = 1L, type = TransactionType.EXPENSE,
                    amountCents = 500L, categoryId = null,
                    description = "Misc", occurredAt = now, createdAt = now
                )
            )
        )

        val result = useCase()

        assertThat(result).hasSize(1)
        assertThat(result[0].categoryName).isNull()
    }

    @Test
    fun `pay events are included as synthetic income rows`() = runTest {
        payEventDao.events = listOf(PayEvent(id = 1L, occurredAt = now, createdAt = now))

        val result = useCase()

        assertThat(result).hasSize(1)
        assertThat(result[0].isPayEvent).isTrue()
        assertThat(result[0].type).isEqualTo(TransactionType.INCOME)
        assertThat(result[0].amountCents).isEqualTo(0L)
        assertThat(result[0].description).isEqualTo("Pay event")
    }

    @Test
    fun `result is sorted by occurredAt ascending`() = runTest {
        val t1 = now - 10_000L
        val t2 = now - 5_000L
        val t3 = now

        transactionDao.setTransactions(
            listOf(
                Transaction(id = 1L, type = TransactionType.EXPENSE, amountCents = 100L, occurredAt = t2, createdAt = now),
                Transaction(id = 2L, type = TransactionType.EXPENSE, amountCents = 200L, occurredAt = t1, createdAt = now)
            )
        )
        payEventDao.events = listOf(PayEvent(id = 1L, occurredAt = t3, createdAt = now))

        val result = useCase()

        assertThat(result).hasSize(3)
        assertThat(result[0].occurredAt).isEqualTo(t1)
        assertThat(result[1].occurredAt).isEqualTo(t2)
        assertThat(result[2].occurredAt).isEqualTo(t3)
    }

    @Test
    fun `mixed income and expense transactions are all included`() = runTest {
        transactionDao.setTransactions(
            listOf(
                Transaction(id = 1L, type = TransactionType.INCOME, amountCents = 5000L, occurredAt = now - 1000, createdAt = now),
                Transaction(id = 2L, type = TransactionType.EXPENSE, amountCents = 1500L, occurredAt = now, createdAt = now)
            )
        )

        val result = useCase()

        assertThat(result).hasSize(2)
        assertThat(result.any { it.type == TransactionType.INCOME && !it.isPayEvent }).isTrue()
        assertThat(result.any { it.type == TransactionType.EXPENSE }).isTrue()
    }

    @Test
    fun `category map handles multiple categories correctly`() = runTest {
        val cats = listOf(
            Category(id = 1L, name = "Food", applicability = CategoryApplicability.EXPENSE, colorHex = "#F00", createdAt = now),
            Category(id = 2L, name = "Salary", applicability = CategoryApplicability.INCOME, colorHex = "#0F0", createdAt = now)
        )
        categoryDao.setCategories(cats)
        transactionDao.setTransactions(
            listOf(
                Transaction(id = 1L, type = TransactionType.EXPENSE, amountCents = 100L, categoryId = 1L, occurredAt = now - 1000, createdAt = now),
                Transaction(id = 2L, type = TransactionType.INCOME, amountCents = 5000L, categoryId = 2L, occurredAt = now, createdAt = now)
            )
        )

        val result = useCase()

        assertThat(result.find { it.amountCents == 100L }?.categoryName).isEqualTo("Food")
        assertThat(result.find { it.amountCents == 5000L }?.categoryName).isEqualTo("Salary")
    }
}
