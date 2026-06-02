package app.getarcane.android.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

// Token colors, matching iOS YAMLHighlighter/EnvHighlighter (dark palette).
private val KeyColor = Color(0xFF80D1FF)
private val CommentColor = Color(0xFF78B878)
private val StringColor = Color(0xFFFABF80)
private val NumberColor = Color(0xFFD1A6FF)
private val BoolColor = Color(0xFFFF9999)
private val AnchorColor = Color(0xFFFFDE73)

private data class HRule(val regex: Regex, val group: Int, val color: Color)

// Rules applied in order; later rules override earlier ones for overlapping ranges (iOS behavior).
private val YAML_RULES = listOf(
    HRule(Regex("(?m)^(\\s*)([\\w\\-./]+)(?=\\s*:)"), 2, KeyColor),
    HRule(Regex("'[^'\\n]*'"), 0, StringColor),
    HRule(Regex("\"[^\"\\n]*\""), 0, StringColor),
    HRule(Regex("(?<=:\\s)\\d+\\.?\\d*\\b"), 0, NumberColor),
    HRule(Regex("(?<=:\\s)\\b(true|false|yes|no|null|~|True|False|Yes|No|Null)\\b"), 0, BoolColor),
    HRule(Regex("[&*][\\w\\-]+"), 0, AnchorColor),
    HRule(Regex("(?m)#.*$"), 0, CommentColor),
)

private val ENV_RULES = listOf(
    HRule(Regex("(?<==)[^\\n]*"), 0, StringColor),
    HRule(Regex("(?m)^\\s*(?:export\\s+)?([A-Za-z_][\\w.\\-]*)(?=\\s*=)"), 1, KeyColor),
    HRule(Regex("(?m)^\\s*#.*$"), 0, CommentColor),
)

/** Syntax-highlight YAML (compose.yml) into a colored [AnnotatedString]. Port of iOS `YAMLHighlighter`. */
fun highlightYaml(code: String): AnnotatedString = highlight(code, YAML_RULES)

/** Syntax-highlight a `.env` file. Port of iOS `EnvHighlighter`. */
fun highlightEnv(code: String): AnnotatedString = highlight(code, ENV_RULES)

private fun highlight(code: String, rules: List<HRule>): AnnotatedString {
    if (code.isEmpty()) return AnnotatedString(code)
    val colors = arrayOfNulls<Color>(code.length)
    for (rule in rules) {
        for (m in rule.regex.findAll(code)) {
            val range = if (rule.group > 0) m.groups[rule.group]?.range else m.range
            if (range != null) for (i in range) if (i in colors.indices) colors[i] = rule.color
        }
    }
    return buildAnnotatedString {
        var i = 0
        while (i < code.length) {
            val color = colors[i]
            var j = i + 1
            while (j < code.length && colors[j] == color) j++
            if (color != null) withStyle(SpanStyle(color = color)) { append(code.substring(i, j)) } else append(code.substring(i, j))
            i = j
        }
    }
}
