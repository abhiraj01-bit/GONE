package com.infinity.ai.health.sharing

import com.infinity.ai.health.data.HealthReportEntity
import com.infinity.ai.health.data.ValueSource
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

object ReportEmailBuilder {

    fun buildSubject(report: HealthReportEntity): String {
        val fmt = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        return "Infinity Health Report — ${fmt.format(Date(report.sessionStart))}"
    }

    fun buildHtml(report: HealthReportEntity): String {
        val dateFmt = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val statusColor = when (report.overallStatus) {
            "Concerning"      -> "#EF4444"
            "Needs Attention" -> "#F59E0B"
            else              -> "#10B981"
        }
        return buildString {
            append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">")
            append("<style>")
            append("body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;background:#0A0E1A;color:#E8EDF5;margin:0;padding:0}")
            append(".wrap{max-width:600px;margin:0 auto;padding:24px 16px}")
            append(".hdr{background:#111827;border-radius:16px;padding:28px;margin-bottom:20px;border:1px solid #1E2D45}")
            append(".logo{font-size:22px;font-weight:700;color:#4F8CFF}")
            append(".sub{color:#7A8BA8;font-size:13px;margin-top:4px}")
            append(".badge{display:inline-block;background:${statusColor}22;color:${statusColor};border:1px solid ${statusColor}44;border-radius:8px;padding:6px 14px;font-size:14px;font-weight:600;margin-top:12px}")
            append(".sec{background:#111827;border-radius:12px;padding:20px;margin-bottom:16px;border:1px solid #1E2D45}")
            append(".stitle{font-size:11px;font-weight:600;color:#4F8CFF;letter-spacing:1px;text-transform:uppercase;margin-bottom:14px}")
            append(".row{display:flex;justify-content:space-between;padding:6px 0;border-bottom:1px solid #1E2D4530}")
            append(".lbl{color:#7A8BA8;font-size:13px}.val{color:#E8EDF5;font-size:13px;font-weight:600}")
            append(".grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}")
            append(".vc{background:#1A2235;border-radius:10px;padding:14px;border:1px solid #1E2D45}")
            append(".vn{font-size:11px;color:#7A8BA8;margin-bottom:6px}")
            append(".va{font-size:22px;font-weight:700;color:#4F8CFF}")
            append(".vu{font-size:11px;color:#7A8BA8;margin-left:3px}")
            append(".vmm{font-size:11px;color:#7A8BA8;margin-top:4px}")
            append(".tag{font-size:9px;font-weight:700;color:#10B981;background:#10B98115;border-radius:4px;padding:2px 5px;margin-left:4px}")
            append(".est{color:#F59E0B;background:#F59E0B15}")
            append("table{width:100%;border-collapse:collapse;font-size:13px}")
            append("th{color:#7A8BA8;font-weight:600;text-align:left;padding:6px 8px;border-bottom:1px solid #1E2D45}")
            append("td{color:#E8EDF5;padding:8px;border-bottom:1px solid #1E2D4530}")
            append(".sn{color:#10B981}.sa{color:#4F8CFF}.se{color:#F59E0B}.ss{color:#EF4444}")
            append("ul{list-style:none;padding:0;margin:0}")
            append("li{padding:5px 0;color:#E8EDF5;font-size:13px;line-height:1.5}")
            append("li::before{content:'• ';color:#4F8CFF}")
            append(".cc{background:#1A2235;border-radius:10px;padding:14px;margin-bottom:10px;border-left:3px solid #EF4444}")
            append(".ct{font-weight:600;font-size:14px;color:#E8EDF5}")
            append(".cd{font-size:13px;color:#7A8BA8;margin-top:4px;line-height:1.5}")
            append(".disc{background:#1A2235;border-radius:10px;padding:16px;font-size:11px;color:#7A8BA8;line-height:1.6;border:1px solid #1E2D45}")
            append(".foot{text-align:center;color:#3D4E65;font-size:11px;margin-top:20px}")
            append("</style></head><body><div class=\"wrap\">")

            // Header
            append("<div class=\"hdr\"><div class=\"logo\">INFINITY</div>")
            append("<div class=\"sub\">Edge Health Intelligence · G-ONE AI</div>")
            append("<div class=\"badge\">${report.overallStatus.ifBlank { "Unknown" }}</div></div>")

            // Session Overview
            append("<div class=\"sec\"><div class=\"stitle\">Session Overview</div>")
            append("<div class=\"row\"><span class=\"lbl\">Date</span><span class=\"val\">${dateFmt.format(Date(report.sessionStart))}</span></div>")
            append("<div class=\"row\"><span class=\"lbl\">Start</span><span class=\"val\">${timeFmt.format(Date(report.sessionStart))}</span></div>")
            append("<div class=\"row\"><span class=\"lbl\">End</span><span class=\"val\">${timeFmt.format(Date(report.sessionEnd))}</span></div>")
            append("<div class=\"row\"><span class=\"lbl\">Duration</span><span class=\"val\">${report.durationMinutes} min</span></div>")
            append("<div class=\"row\"><span class=\"lbl\">Valid Readings</span><span class=\"val\">${report.totalReadings}</span></div>")
            if (report.fallEventCount > 0)
                append("<div class=\"row\"><span class=\"lbl\" style=\"color:#EF4444\">Fall Events</span><span class=\"val\" style=\"color:#EF4444\">${report.fallEventCount}</span></div>")
            append("</div>")

            // Vitals
            append("<div class=\"sec\"><div class=\"stitle\">Vitals Summary</div><div class=\"grid\">")
            val emgAvg = report.emgAverage?.let { "%.0f".format(it) } ?: "--"
            append("<div class=\"vc\"><div class=\"vn\">EMG <span class=\"tag\">LIVE</span></div>")
            append("<div><span class=\"va\">$emgAvg</span><span class=\"vu\">ADC</span></div>")
            append("<div class=\"vmm\">Min ${report.emgMin ?: "--"} | Max ${report.emgMax ?: "--"}</div></div>")

            val bpmAvg = report.bpmAverage?.let { "%.0f".format(it) } ?: "--"
            append("<div class=\"vc\"><div class=\"vn\">Heart Rate <span class=\"tag\">LIVE</span></div>")
            append("<div><span class=\"va\">$bpmAvg</span><span class=\"vu\">BPM</span></div>")
            append("<div class=\"vmm\">Min ${report.bpmMin ?: "--"} | Max ${report.bpmMax ?: "--"}</div></div>")

            val tempVal = report.temperature?.let { "%.1f".format(it) } ?: "--"
            val tempSrc = if (report.temperatureSource == ValueSource.DERIVED) "EST." else "N/A"
            append("<div class=\"vc\"><div class=\"vn\">Temperature <span class=\"tag est\">$tempSrc</span></div>")
            append("<div><span class=\"va\">$tempVal</span><span class=\"vu\">°C</span></div>")
            append("<div class=\"vmm\">Estimated, not measured</div></div>")

            val spo2Val = report.spo2?.toString() ?: "--"
            val spo2Src = if (report.spo2Source == ValueSource.DERIVED) "EST." else "N/A"
            append("<div class=\"vc\"><div class=\"vn\">SpO2 <span class=\"tag est\">$spo2Src</span></div>")
            append("<div><span class=\"va\">$spo2Val</span><span class=\"vu\">%</span></div>")
            append("<div class=\"vmm\">Estimated, not measured</div></div>")
            append("</div></div>")

            // Session Trend
            val points = parseRepPoints(report.representativeReadingsJson)
            if (points.isNotEmpty()) {
                append("<div class=\"sec\"><div class=\"stitle\">Session Trend</div>")
                append("<table><tr><th>Point</th><th>EMG</th><th>BPM</th><th>Status</th></tr>")
                points.forEach { pt ->
                    val sc = when (pt.status) { "Spasm" -> "ss"; "Elevated" -> "se"; "Active" -> "sa"; else -> "sn" }
                    append("<tr><td>${pt.label}</td><td>${pt.emg}</td><td>${pt.bpm}</td><td class=\"$sc\">${pt.status}</td></tr>")
                }
                append("</table></div>")
            }

            // AI Summary
            if (report.aiSummary.isNotBlank()) {
                append("<div class=\"sec\"><div class=\"stitle\">AI Summary</div>")
                append("<p style=\"color:#E8EDF5;font-size:13px;line-height:1.6;margin:0\">${esc(report.aiSummary)}</p></div>")
            }

            // Observations
            val obs = parseArr(report.observationsJson)
            if (obs.isNotEmpty()) {
                append("<div class=\"sec\"><div class=\"stitle\">Key Observations</div><ul>")
                obs.forEach { append("<li>${esc(it)}</li>") }
                append("</ul></div>")
            }

            // Physical Concerns
            val concerns = parseConcerns(report.physicalConcernsJson)
            if (concerns.isNotEmpty()) {
                append("<div class=\"sec\"><div class=\"stitle\">Physical Concerns</div>")
                concerns.forEach { c ->
                    val bc = when (c[2].lowercase()) { "high" -> "#EF4444"; "moderate" -> "#F59E0B"; else -> "#10B981" }
                    append("<div class=\"cc\" style=\"border-left-color:$bc\">")
                    append("<div class=\"ct\">${esc(c[0])}</div>")
                    append("<div class=\"cd\">${esc(c[1])}</div></div>")
                }
                append("</div>")
            }

            // Food
            val food = parseArr(report.foodRecommendationsJson)
            if (food.isNotEmpty()) {
                append("<div class=\"sec\"><div class=\"stitle\">Recommended Nutrition</div><ul>")
                food.forEach { append("<li>${esc(it)}</li>") }
                append("</ul></div>")
            }

            // Exercise
            val exercise = parseArr(report.exerciseRecommendationsJson)
            if (exercise.isNotEmpty()) {
                append("<div class=\"sec\"><div class=\"stitle\">Recommended Activity</div><ul>")
                exercise.forEach { append("<li>${esc(it)}</li>") }
                append("</ul></div>")
            }

            // Lifestyle
            val lifestyle = parseArr(report.lifestyleRecommendationsJson)
            if (lifestyle.isNotEmpty()) {
                append("<div class=\"sec\"><div class=\"stitle\">Lifestyle &amp; Recovery</div><ul>")
                lifestyle.forEach { append("<li>${esc(it)}</li>") }
                append("</ul></div>")
            }

            // Medical Attention
            val medical = parseArr(report.medicalAttentionJson)
            if (medical.isNotEmpty()) {
                append("<div class=\"sec\" style=\"border:1px solid #EF444440\">")
                append("<div class=\"stitle\" style=\"color:#EF4444\">When to Seek Medical Attention</div><ul>")
                medical.forEach { append("<li>${esc(it)}</li>") }
                append("</ul></div>")
            }

            // Disclaimer + Footer
            append("<div class=\"disc\"><strong>Disclaimer:</strong> This report is an AI-assisted interpretation of sensor data and is not a medical diagnosis. Recommendations are general wellness guidance only and not a substitute for clinical advice.</div>")
            append("<div class=\"foot\">Infinity · Edge Health Intelligence · G-ONE AI Engine</div>")
            append("</div></body></html>")
        }
    }

    private data class RepPoint(val label: String, val emg: String, val bpm: String, val status: String)

    private fun parseRepPoints(json: String): List<RepPoint> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            RepPoint(
                label  = o.optString("label", "--"),
                emg    = o.opt("emg")?.let { if (it.toString() == "null") "--" else it.toString() } ?: "--",
                bpm    = o.opt("bpm")?.let { if (it.toString() == "null") "--" else it.toString() } ?: "--",
                status = o.optString("status", "Normal")
            )
        }
    } catch (e: Exception) { emptyList() }

    private fun parseArr(json: String): List<String> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) { emptyList() }

    // Returns list of [title, description, severity]
    private fun parseConcerns(json: String): List<List<String>> = try {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            listOf(o.optString("title"), o.optString("description"), o.optString("severity", "low"))
        }
    } catch (e: Exception) { emptyList() }

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}
