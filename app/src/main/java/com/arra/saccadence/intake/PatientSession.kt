package com.arra.saccadence.intake

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.UUID

enum class SymptomFlag(val question: String) {
    DIZZY("Feeling dizzy or unbalanced today?"),
    HEAD_INJURY("Recent head injury or concussion?"),
    POOR_SLEEP("Slept poorly last night?"),
    ALERTNESS_MEDICATION("On any medication affecting alertness (antihistamines, sedatives)?"),
    GLASSES_OR_CONTACTS("Wearing glasses or contacts right now?"),
    CAFFEINE_OR_ALCOHOL("Consumed caffeine or alcohol in the last few hours?"),
    EYE_STRAIN_OR_HEADACHE("Any current eye strain or headache?"),
}

/** Single shared state object for the whole intake flow, hoisted above navigation. */
class PatientSession {
    var id by mutableStateOf(UUID.randomUUID().toString())
    var consentGivenAt by mutableStateOf<Long?>(null)
    var name by mutableStateOf("")
    var age by mutableStateOf<Int?>(null)
    var mobile by mutableStateOf("")
    var doctorName by mutableStateOf("")
    val symptoms = mutableStateMapOf<SymptomFlag, Boolean>().apply {
        SymptomFlag.entries.forEach { put(it, false) }
    }
    var notes by mutableStateOf("")

    val isConsentGiven: Boolean get() = consentGivenAt != null
    val isDetailsValid: Boolean get() = name.isNotBlank() && (age ?: -1) in 1..120
    val isDoctorValid: Boolean get() = doctorName.isNotBlank()

    fun resetTo(record: PatientRecord = PatientRecord.blank()) {
        id = record.id
        consentGivenAt = record.consentGivenAt
        name = record.name
        age = record.age
        mobile = record.mobile
        doctorName = record.doctorName
        SymptomFlag.entries.forEach { symptoms[it] = record.symptoms.contains(it.name) }
        notes = record.notes
    }

    fun toRecord(): PatientRecord = PatientRecord(
        id = id,
        consentGivenAt = consentGivenAt,
        name = name,
        age = age,
        mobile = mobile,
        doctorName = doctorName,
        symptoms = symptoms.filterValues { it }.keys.map { it.name }.toSet(),
        notes = notes,
        savedAt = System.currentTimeMillis(),
    )

    /** Fills every field with realistic sample data and marks consent as given. */
    fun fillDemoData() {
        id = UUID.randomUUID().toString()
        consentGivenAt = System.currentTimeMillis()
        name = "Ramesh Kulkarni"
        age = 47
        mobile = ""
        doctorName = "Dr. Anjali Rao"
        symptoms[SymptomFlag.DIZZY] = false
        symptoms[SymptomFlag.HEAD_INJURY] = false
        symptoms[SymptomFlag.POOR_SLEEP] = true
        symptoms[SymptomFlag.ALERTNESS_MEDICATION] = false
        symptoms[SymptomFlag.GLASSES_OR_CONTACTS] = true
        symptoms[SymptomFlag.CAFFEINE_OR_ALCOHOL] = true
        symptoms[SymptomFlag.EYE_STRAIN_OR_HEADACHE] = false
        notes = "Reports mild eye strain after long screen use this week."
    }
}

/** Plain, JSON-serializable snapshot of [PatientSession] for local persistence. */
data class PatientRecord(
    val id: String,
    val consentGivenAt: Long?,
    val name: String,
    val age: Int?,
    val mobile: String,
    val doctorName: String,
    val symptoms: Set<String>,
    val notes: String,
    val savedAt: Long,
) {
    companion object {
        fun blank() = PatientRecord(
            id = UUID.randomUUID().toString(),
            consentGivenAt = null,
            name = "",
            age = null,
            mobile = "",
            doctorName = "",
            symptoms = emptySet(),
            notes = "",
            savedAt = System.currentTimeMillis(),
        )
    }
}
