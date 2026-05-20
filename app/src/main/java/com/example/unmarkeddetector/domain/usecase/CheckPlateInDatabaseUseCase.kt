package com.example.unmarkeddetector.domain.usecase

import com.example.unmarkeddetector.domain.model.PlateRecord
import com.example.unmarkeddetector.domain.repository.PlateRepository
import java.util.Locale
import javax.inject.Inject

class CheckPlateInDatabaseUseCase @Inject constructor(
    private val plateRepository: PlateRepository
) {
    suspend operator fun invoke(plate: String): PlateRecord? {
        val normalized = plate.uppercase(Locale.ROOT).replace("\\s".toRegex(), "")
        return candidateVariants(normalized).firstNotNullOfOrNull { variant ->
            plateRepository.findByPlate(variant)
        }
    }

    private fun candidateVariants(plate: String): List<String> {
        val prefixMaps = mapOf(
            '0' to 'O',
            '1' to 'I',
            '2' to 'Z',
            '5' to 'S',
            '6' to 'G',
            '8' to 'B'
        )
        val suffixMaps = mapOf(
            'O' to '0',
            'Q' to '0',
            'I' to '1',
            'L' to '1',
            'Z' to '2',
            'S' to '5',
            'B' to '8'
        )

        val variants = linkedSetOf(plate)
        val prefixLengths = (2..3).filter { it < plate.length && plate.length - it in 4..5 }

        prefixLengths.forEach { prefixLength ->
            val prefix = plate.take(prefixLength)
            val suffix = plate.drop(prefixLength)

            val normalizedPrefix = prefix.map { char -> prefixMaps[char] ?: char }.joinToString("")
            variants.add(normalizedPrefix + suffix)

            suffix.forEachIndexed { index, char ->
                suffixMaps[char]?.let { replacement ->
                    variants.add(normalizedPrefix + suffix.replaceRange(index, index + 1, replacement.toString()))
                }
            }
        }

        return variants.toList()
    }
}
