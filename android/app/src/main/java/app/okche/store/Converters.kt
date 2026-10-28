package app.okche.store

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

/** 목록 칸을 JSON 글자로 저장한다(안드로이드 내장 org.json). */
class Converters {
    @TypeConverter fun fromStrings(value: List<String>): String = JSONArray(value).toString()

    @TypeConverter fun toStrings(json: String): List<String> = JSONArray(json).let { a -> List(a.length()) { a.getString(it) } }

    @TypeConverter fun fromInts(value: List<Int>): String = JSONArray(value).toString()

    @TypeConverter fun toInts(json: String): List<Int> = JSONArray(json).let { a -> List(a.length()) { a.getInt(it) } }

    @TypeConverter fun fromInsulinSlots(value: List<InsulinSlot>): String =
        JSONArray(value.map { JSONObject().put("time", it.time).put("units", it.units) }).toString()

    @TypeConverter fun toInsulinSlots(json: String): List<InsulinSlot> = JSONArray(json).let { a ->
        List(a.length()) { a.getJSONObject(it).run { InsulinSlot(getString("time"), getDouble("units")) } }
    }

    @TypeConverter fun fromMealSlots(value: List<MealSlot>): String =
        JSONArray(value.map { JSONObject().put("name", it.name).put("time", it.time) }).toString()

    @TypeConverter fun toMealSlots(json: String): List<MealSlot> = JSONArray(json).let { a ->
        List(a.length()) { a.getJSONObject(it).run { MealSlot(getString("name"), getString("time")) } }
    }
}
