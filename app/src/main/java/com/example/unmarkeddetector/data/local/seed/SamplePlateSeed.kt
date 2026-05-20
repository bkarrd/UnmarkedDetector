package com.example.unmarkeddetector.data.local.seed

import com.example.unmarkeddetector.data.local.entity.PlateRecordEntity

object SamplePlateSeed {

    private val baseTimestamp = 1_711_886_400_000L

    val records = listOf(
        PlateRecordEntity("HPA4K92", "Skoda", "Superb", "mazowieckie", baseTimestamp, 14),
        PlateRecordEntity("HPA7L31", "BMW", "530d", "mazowieckie", baseTimestamp + 1, 18),
        PlateRecordEntity("KRA8M44", "Volkswagen", "Passat", "malopolskie", baseTimestamp + 2, 9),
        PlateRecordEntity("KRA5N17", "Audi", "A6", "malopolskie", baseTimestamp + 3, 16),
        PlateRecordEntity("WPI2C88", "Toyota", "Camry", "mazowieckie", baseTimestamp + 4, 12),
        PlateRecordEntity("WZ9A224", "Hyundai", "i30", "mazowieckie", baseTimestamp + 5, 8),
        PlateRecordEntity("GD3R510", "Kia", "Ceed", "pomorskie", baseTimestamp + 6, 11),
        PlateRecordEntity("GSP7D22", "Skoda", "Octavia", "pomorskie", baseTimestamp + 7, 7),
        PlateRecordEntity("PO5A610", "Ford", "Mondeo", "wielkopolskie", baseTimestamp + 8, 15),
        PlateRecordEntity("PZ3X221", "Volkswagen", "Arteon", "wielkopolskie", baseTimestamp + 9, 5),
        PlateRecordEntity("DW4K118", "BMW", "320d", "dolnoslaskie", baseTimestamp + 10, 13),
        PlateRecordEntity("DB7T904", "Skoda", "Kodiaq", "dolnoslaskie", baseTimestamp + 11, 6),
        PlateRecordEntity("LU8M491", "Audi", "Q5", "lubelskie", baseTimestamp + 12, 10),
        PlateRecordEntity("LUB4F27", "Mercedes", "Vito", "lubelskie", baseTimestamp + 13, 4),
        PlateRecordEntity("RZE2A63", "Opel", "Insignia", "podkarpackie", baseTimestamp + 14, 8),
        PlateRecordEntity("RJA5B44", "Toyota", "Corolla", "podkarpackie", baseTimestamp + 15, 5),
        PlateRecordEntity("SCI4S99", "Volvo", "S90", "slaskie", baseTimestamp + 16, 9),
        PlateRecordEntity("SB6X102", "Skoda", "Superb", "slaskie", baseTimestamp + 17, 17),
        PlateRecordEntity("EL7N331", "BMW", "X3", "lodzkie", baseTimestamp + 18, 6),
        PlateRecordEntity("EPA9J07", "Seat", "Leon", "lodzkie", baseTimestamp + 19, 3),
        PlateRecordEntity("NO2R710", "Ford", "Focus", "warminsko-mazurskie", baseTimestamp + 20, 5),
        PlateRecordEntity("NE4M552", "Audi", "A4", "warminsko-mazurskie", baseTimestamp + 21, 7),
        PlateRecordEntity("ZS8K431", "Skoda", "Rapid", "zachodniopomorskie", baseTimestamp + 22, 4),
        PlateRecordEntity("ZPL1T87", "Toyota", "RAV4", "zachodniopomorskie", baseTimestamp + 23, 6)
    )
}
