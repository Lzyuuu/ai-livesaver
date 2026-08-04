package io.github.lzyuuu.ailivesaver

import org.json.JSONArray
import org.json.JSONObject

/** Stable, deliberately small Binder payload; persisted as JSON inside frozen contracts. */
internal data class BinderAnswers(
    val relationship: String = "", val preferences: String = "", val personality: String = "",
    val communication: String = "", val interests: String = "", val boundaries: String = "", val exclusions: String = "",
) {
    fun toJson() = JSONObject().put("relationship", relationship).put("preferences", preferences)
        .put("personality", personality).put("communication", communication).put("interests", interests)
        .put("boundaries", boundaries).put("exclusions", exclusions).toString()
    companion object { fun fromJson(raw: String): BinderAnswers { val o=JSONObject(raw); return BinderAnswers(o.optString("relationship"),o.optString("preferences"),o.optString("personality"),o.optString("communication"),o.optString("interests"),o.optString("boundaries"),o.optString("exclusions")) } }
}

internal data class BinderCandidatePayload(val name: String, val persona: String, val relationship: String, val reasons: List<String>) {
    fun toJson() = JSONObject().put("name",name).put("persona",persona).put("relationship",relationship).put("reasons",JSONArray(reasons)).toString()
    companion object { fun parse(raw: String): BinderCandidatePayload { val o=JSONObject(raw); val n=o.optString("name").trim(); val p=o.optString("persona").trim(); require(n.isNotEmpty() && p.isNotEmpty()) { "候选缺少 name/persona" }; val a=o.optJSONArray("reasons") ?: throw IllegalArgumentException("候选缺少 reasons"); require(a.length() in 1..8) { "匹配理由数量无效" }; require(o.has("relationship")) { "候选缺少 relationship" }; return BinderCandidatePayload(n,p,o.optString("relationship"),buildList { for(i in 0 until a.length()) a.optString(i).trim().takeIf(String::isNotEmpty)?.let(::add) }) } }
}

internal fun validateBinderCandidateList(raw: String): List<BinderCandidatePayload> {
    val a = JSONObject(raw).optJSONArray("candidates") ?: throw IllegalArgumentException("候选必须包含 candidates 数组")
    require(a.length() in 1..6) { "候选数量无效" }
    return buildList { for (i in 0 until a.length()) add(BinderCandidatePayload.parse(a.getJSONObject(i).toString())) }
}
