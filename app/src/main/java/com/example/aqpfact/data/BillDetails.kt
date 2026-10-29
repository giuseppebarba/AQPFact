package com.example.aqpfact.data

data class BillDetails(
    val total: String = "0.0",
    val fixed: String = "0.0",
    val aqueduct: String = "0.0",
    val sewerage: String = "0.0",
    val purification: String = "0.0",
    val charges: String = "0.0",
    val bonus: String = "0.0",
    val advances: String = "0.0",
    val other: String = "0.0",
    val taxable: String = "0.0"
) {
    private fun String.toCleanDouble(): Double =
        this.replace(',', '.').trim().toDoubleOrNull() ?: 0.0

    val totalDouble: Double get() = total.toCleanDouble()
    val fixedDouble: Double get() = fixed.toCleanDouble()
    val aqueductDouble: Double get() = aqueduct.toCleanDouble()
    val sewerageDouble: Double get() = sewerage.toCleanDouble()
    val purificationDouble: Double get() = purification.toCleanDouble()
    val chargesDouble: Double get() = charges.toCleanDouble()
    val bonusDouble: Double get() = bonus.toCleanDouble()
    val advancesDouble: Double get() = advances.toCleanDouble()
    val otherDouble: Double get() = other.toCleanDouble()
    val taxableDouble: Double get() = taxable.toCleanDouble()

    // IVA 10% sul Totale Imponibile inserito dall'utente
    val vat10Double: Double get() = taxableDouble * 0.10

    // Costi variabili = Servizio acquedotto + Servizio Fognatura + Servizio depurazione + Oneri + Addebiti/Accrediti diversi + IVA
    val variableDouble: Double
        get() = aqueductDouble + sewerageDouble + purificationDouble + chargesDouble + otherDouble + vat10Double

    // Costo totale = somma dei costi fissi e dei costi variabili
    val totalCostDouble: Double
        get() = fixedDouble + variableDouble

    val calculatedTotalDouble: Double
        get() = totalCostDouble

    // Percentuale della quota coperta dagli acconti = rapporto tra Acconti precedenti e Costo Totale
    val advancesPercentage: Double
        get() = if (totalCostDouble > 0) kotlin.math.abs(advancesDouble) / totalCostDouble else 0.0

    /**
     * Calcola la ripartizione per un'utenza:
     * - Quota fissa divisa per numero utenze (default 3)
     * - Quota variabile proporzionale ai m³ consumati
     * - Quota lorda = Quota fissa + Quota variabile
     * - Sconto acconti = Quota lorda * Percentuale acconti
     * - Quota netta = Quota lorda - Sconto acconti
     */
    fun calculateUserShare(
        userConsumption: Double,
        totalConsumption: Double,
        numUsers: Int = 3
    ): UserShare {
        val fixedPerUser = if (numUsers > 0) fixedDouble / numUsers else 0.0
        val variableShare = if (totalConsumption > 0) (userConsumption / totalConsumption) * variableDouble else 0.0
        val grossShare = fixedPerUser + variableShare
        val discount = grossShare * advancesPercentage
        val netShare = grossShare - discount

        return UserShare(
            fixedShare = fixedPerUser,
            variableShare = variableShare,
            grossShare = grossShare,
            discount = discount,
            netShare = netShare
        )
    }

    data class UserShare(
        val fixedShare: Double,
        val variableShare: Double,
        val grossShare: Double,
        val discount: Double,
        val netShare: Double
    )
}
