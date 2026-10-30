package com.farhan.paisa.data

import com.farhan.paisa.domain.SmsParser
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** A believable few months of activity, so the app can be explored before entering real data. */
suspend fun Repository.loadSampleData() {
    val random = Random(42)
    val today = LocalDate.now()

    insertDefaultCategories()
    val category = ledger.first().categories.associate { it.name to it.id }

    val daily = saveAccount(Account(name = "Daily expenses", kind = AccountKind.BANK, last4 = "4417", opening = 42_000_00, accent = 0))
    val savings = saveAccount(Account(name = "Savings", kind = AccountKind.BANK, role = AccountRole.SAVINGS, last4 = "9032", opening = 1_85_000_00, accent = 1, sortOrder = 1))
    val rewards = saveAccount(
        Account(
            name = "Rewards Card",
            kind = AccountKind.CREDIT_CARD,
            last4 = "3281",
            opening = 8_400_00,
            creditLimit = 2_00_000_00,
            statementDay = 8,
            dueDay = 3,
            accent = 2,
            sortOrder = 2,
        ),
    )
    val travel = saveAccount(
        Account(
            name = "Travel Card",
            kind = AccountKind.CREDIT_CARD,
            last4 = "7750",
            opening = 2_150_00,
            creditLimit = 1_50_000_00,
            statementDay = 18,
            dueDay = 7,
            accent = 3,
            sortOrder = 3,
        ),
    )

    val football = addTag("football")
    val friends = addTag("friends")
    val office = addTag("office")
    val shubham = addPerson("Shubham")
    val rahul = addPerson("Rahul")
    val priya = addPerson("Priya")

    fun at(date: LocalDate, hour: Int): Long =
        date.atTime(hour, random.nextInt(60)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    suspend fun spend(
        date: LocalDate,
        amount: Long,
        payee: String,
        categoryName: String,
        account: Long = daily,
        method: PayMethod = PayMethod.UPI,
        tags: List<Long> = emptyList(),
        shares: List<Pair<Long, Long>> = emptyList(),
    ) {
        if (date.isAfter(today)) return
        val txn = Txn(
            type = TxType.EXPENSE,
            amount = amount,
            accountId = account,
            categoryId = category[categoryName],
            payee = payee,
            method = method,
            date = at(date, 10 + random.nextInt(11)),
        )
        saveTxn(txn, tags, shares)
    }

    suspend fun move(date: LocalDate, amount: Long, from: Long, to: Long, method: PayMethod = PayMethod.NET_BANKING) {
        if (date.isAfter(today)) return
        saveTxn(Txn(type = TxType.TRANSFER, amount = amount, accountId = from, toAccountId = to, method = method, date = at(date, 9)), emptyList(), emptyList())
    }

    for (back in 3 downTo 0) {
        val first = today.withDayOfMonth(1).minusMonths(back.toLong())
        val days = first.lengthOfMonth()

        if (!first.isAfter(today)) {
            saveTxn(
                Txn(type = TxType.INCOME, amount = 85_000_00, accountId = daily, categoryId = category["Salary"], payee = "Salary", method = PayMethod.NET_BANKING, date = at(first, 9)),
                emptyList(),
                emptyList(),
            )
        }
        move(first.plusDays(1), 15_000_00 + 2_000_00L * (3 - back), daily, savings)
        spend(first.plusDays(2), 18_000_00, "Landlord", "Home & rent", method = PayMethod.NET_BANKING)
        spend(first.plusDays(3), 649_00, "Netflix", "Subscriptions", rewards, PayMethod.CARD)
        spend(first.plusDays(4), 1_299_00, "Airtel", "Bills", method = PayMethod.AUTO_DEBIT)
        spend(first.plusDays(9), 2_340_00, "Electricity", "Bills", method = PayMethod.UPI)
        move(first.plusDays(14), 14_000_00, daily, rewards)
        move(first.plusDays(15), 4_379_00, daily, travel)

        var day = 1
        while (day <= days) {
            spend(first.plusDays(day - 1L), 150_00, "Shubham", "Sports", tags = listOf(football))
            day += 2
        }
        repeat(11) {
            val date = first.plusDays(random.nextLong(days.toLong()))
            val (payee, name) = listOf(
                "Swiggy" to "Food & dining",
                "Zomato" to "Food & dining",
                "Blue Tokai" to "Food & dining",
                "Blinkit" to "Groceries",
                "Zepto" to "Groceries",
                "Uber" to "Transport",
                "Rapido" to "Transport",
            ).random(random)
            val amount = (180 + random.nextInt(620)).toLong() * 100
            spend(date, amount, payee, name, tags = if (name == "Food & dining" && random.nextBoolean()) listOf(office) else emptyList())
        }
        repeat(3) {
            val date = first.plusDays(random.nextLong(days.toLong()))
            spend(date, (1_200 + random.nextInt(4_000)).toLong() * 100, listOf("Myntra", "Amazon", "Decathlon").random(random), "Shopping", rewards, PayMethod.CARD)
        }
        spend(first.plusDays(19), 2_480_00, "BookMyShow", "Entertainment", travel, PayMethod.CARD, listOf(friends), listOf(rahul to 827_00, priya to 827_00))
        spend(first.plusDays(21), 4_800_00, "Social Bar", "Food & dining", rewards, PayMethod.CARD, listOf(friends), listOf(shubham to 1_200_00, rahul to 1_200_00, priya to 1_200_00))
        spend(first.plusDays(24), 1_899_00, "Indigo", "Travel", travel, PayMethod.CARD)

        if (back > 0) {
            val paid = first.plusDays(26)
            listOf(shubham to 1_200_00L, rahul to 2_027_00L, priya to 2_027_00L).forEach { (person, amount) ->
                saveTxn(Txn(type = TxType.SETTLEMENT, amount = amount, accountId = daily, personId = person, date = at(paid, 20)), emptyList(), emptyList())
            }
        }
    }

    saveRecurring(Recurring(name = "Landlord", amount = 18_000_00, day = 3, categoryId = category["Home & rent"], accountId = daily, method = PayMethod.NET_BANKING))
    saveRecurring(Recurring(name = "Netflix", amount = 649_00, day = 4, categoryId = category["Subscriptions"], accountId = rewards, method = PayMethod.CARD))
    saveRecurring(Recurring(name = "Airtel", amount = 1_299_00, day = 5, categoryId = category["Bills"], accountId = daily, method = PayMethod.AUTO_DEBIT))
    saveRecurring(
        Recurring(
            name = "Gym",
            amount = 1_500_00,
            day = (today.dayOfMonth + 1).coerceAtMost(today.lengthOfMonth()),
            categoryId = category["Health"],
            accountId = daily,
        ),
    )

    val now = System.currentTimeMillis()
    val emergency = saveGoal(Goal(name = "Emergency fund", target = 1_50_000_00, accent = 1))
    val trip = saveGoal(Goal(name = "Goa trip", target = 30_000_00, targetDay = today.plusMonths(5).toEpochDay(), accent = 2))
    addToGoal(emergency, 90_000_00, now, null)
    addToGoal(trip, 12_000_00, now, null)

    val ddMmYy = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yy")
    listOf(
        "AX-KOTAKB-S" to "Sent Rs.458.00 from Kotak Bank A/c X4417 to ZEPTO MARKETPLACE PR on ${today.format(ddMmYy)}. UPI Ref 700000000001.",
        "AD-AXISBK-S" to "Spent INR 8750 using Axis Bank Credit Card no. XX3281 on ${today.format(ddMmYy)} 18:44:56 IST at MAKEMYTRIP. Avl limit: INR 41230.50.",
    ).forEach { (sender, body) ->
        SmsParser.parse(sender, body)?.let { ingest(SmsParser.toDetected(it, body, now)) }
    }

    settings.update {
        it.copy(name = "Demo", onboarded = true, monthlyBudget = 45_000_00, savingsTarget = 20_000_00)
    }
    val categories = ledger.first().categories
    categories.firstOrNull { it.name == "Food & dining" }?.let { saveCategory(it.copy(monthlyLimit = 8_000_00)) }
    categories.firstOrNull { it.name == "Sports" }?.let { saveCategory(it.copy(monthlyLimit = 3_000_00)) }
}
