package com.arra.saccadence.intake

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Local-only JSON store, internal app storage. Nothing here is ever transmitted —
 * that property is load-bearing for the consent screen's privacy claims, so don't
 * add a sync/export path later without revisiting Step 0's copy.
 */
class PatientRepository(context: Context) {
    private val file = File(context.filesDir, "patients.json")

    fun loadAll(): List<PatientRecord> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { i -> array.getJSONObject(i).toRecord() }
        }.getOrDefault(emptyList())
    }

    fun save(record: PatientRecord) {
        val records = loadAll().filterNot { it.id == record.id } + record
        writeAll(records)
    }

    fun delete(id: String) {
        writeAll(loadAll().filterNot { it.id == id })
    }

    /**
     * Wipes every stored record. This is the bulk half of the erasure right the
     * consent notice promises, so it deletes the file outright rather than
     * writing an empty array — there should be nothing left on disk to recover.
     */
    fun deleteAll() {
        file.delete()
    }

    fun findByNameAndAge(name: String, age: Int?): PatientRecord? =
        loadAll()
            .filter { age == null || it.age == age }
            .sortedByDescending { it.savedAt }
            .firstOrNull { it.name.trim().equals(name.trim(), ignoreCase = true) }

    private fun writeAll(records: List<PatientRecord>) {
        val array = JSONArray()
        records.forEach { array.put(it.toJson()) }
        file.writeText(array.toString())
    }

    private fun PatientRecord.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("consentGivenAt", consentGivenAt ?: JSONObject.NULL)
        put("name", name)
        put("age", age ?: JSONObject.NULL)
        put("mobile", mobile)
        put("doctorName", doctorName)
        put("symptoms", JSONArray(symptoms.toList()))
        put("notes", notes)
        put("savedAt", savedAt)
    }

    private fun JSONObject.toRecord(): PatientRecord = PatientRecord(
        id = getString("id"),
        consentGivenAt = if (isNull("consentGivenAt")) null else getLong("consentGivenAt"),
        name = optString("name", ""),
        age = if (isNull("age")) null else getInt("age"),
        mobile = optString("mobile", ""),
        doctorName = optString("doctorName", ""),
        symptoms = optJSONArray("symptoms")?.let { arr ->
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } ?: emptySet(),
        notes = optString("notes", ""),
        savedAt = optLong("savedAt", 0L),
    )
}
