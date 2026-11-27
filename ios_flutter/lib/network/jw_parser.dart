/// 正方教务页面的纯文本解析逻辑（与 Android `JwParser` 一致，便于单元测试）。
class JwParser {
  static String? selectedOptionValue(String html, String id) {
    final selectRegex = RegExp(
      '<select[^>]*\\bid\\s*=\\s*["\']$id["\'][\\s\\S]*?</select>',
      caseSensitive: false,
    );
    final selectMatch = selectRegex.firstMatch(html);
    if (selectMatch == null) return null;
    final selectHtml = selectMatch.group(0)!;
    final optRegex = RegExp(
      '<option[^>]*\\bvalue\\s*=\\s*["\']([^"\']*)["\'][^>]*\\bselected\\b',
      caseSensitive: false,
    );
    return optRegex.firstMatch(selectHtml)?.group(1);
  }

  static List<(String, String)> parseSelectOptions(String html, String id) {
    final selectRegex = RegExp(
      '<select[^>]*\\bid\\s*=\\s*["\']$id["\'][\\s\\S]*?</select>',
      caseSensitive: false,
    );
    final selectMatch = selectRegex.firstMatch(html);
    if (selectMatch == null) return const [];
    final selectHtml = selectMatch.group(0)!;
    final optRegex = RegExp(
      '<option[^>]*\\bvalue\\s*=\\s*["\']([^"\']*)["\'][^>]*>([\\s\\S]*?)</option>',
      caseSensitive: false,
    );
    return optRegex.allMatches(selectHtml).map((m) {
      final value = m.group(1) ?? '';
      final text = decodeEntities(
        (m.group(2) ?? '').replaceAll(RegExp('<[^>]+>'), ''),
      ).trim();
      return (value, text);
    }).toList();
  }

  static String? parseInputValue(String html, String id) {
    final tagRegex = RegExp(
      '<input[^>]*\\bid\\s*=\\s*["\']$id["\'][^>]*>',
      caseSensitive: false,
    );
    final tag = tagRegex.firstMatch(html)?.group(0);
    if (tag == null) return null;
    final valRegex = RegExp(
      '\\bvalue\\s*=\\s*["\']([^"\']*)["\']',
      caseSensitive: false,
    );
    return valRegex.firstMatch(tag)?.group(1);
  }

  static String decodeEntities(String text) {
    return text
        .replaceAll('&nbsp;', ' ')
        .replaceAll('&amp;', '&')
        .replaceAll('&lt;', '<')
        .replaceAll('&gt;', '>')
        .replaceAll('&quot;', '"')
        .replaceAll('&#39;', "'")
        .replaceAll('&#x27;', "'");
  }

  static (int, int)? parsePeriods(String text) {
    final match = RegExp(r'(\d+)(?:\s*-\s*(\d+))?').firstMatch(text);
    if (match == null) return null;
    final start = int.tryParse(match.group(1) ?? '');
    if (start == null) return null;
    final endStr = match.group(2);
    final end = (endStr != null && endStr.isNotEmpty)
        ? (int.tryParse(endStr) ?? start)
        : start;
    return (start, end);
  }

  /// 解析考试时间，返回 `(yyyy-MM-dd, HH:mm, HH:mm)`。
  static (String, String, String) parseExamTime(String text) {
    final trimmed = text.trim();
    if (trimmed.isEmpty) return ('', '', '');

    final isoDate = RegExp(r'(\d{4})-(\d{1,2})-(\d{1,2})').firstMatch(trimmed);
    final cnDate = RegExp(r'(\d{4})\s*年\s*(\d{1,2})\s*月\s*(\d{1,2})\s*日')
        .firstMatch(trimmed);
    final dateMatch = isoDate ?? cnDate;
    var date = '';
    if (dateMatch != null) {
      final year = dateMatch.group(1)!;
      final month = int.tryParse(dateMatch.group(2)!);
      final day = int.tryParse(dateMatch.group(3)!);
      if (month != null && day != null) {
        date =
            '$year-${month.toString().padLeft(2, '0')}-${day.toString().padLeft(2, '0')}';
      }
    }

    final range = RegExp(r'(\d{1,2}:\d{2})\s*[-~～至]\s*(\d{1,2}:\d{2})')
        .firstMatch(trimmed);
    final startRaw =
        range?.group(1) ?? RegExp(r'(\d{1,2}:\d{2})').firstMatch(trimmed)?.group(1);
    final startClock = startRaw != null ? _normalizeClock(startRaw) : '';
    final endClock =
        range?.group(2) != null ? _normalizeClock(range!.group(2)!) : '';
    return (date, startClock, endClock);
  }

  static String _normalizeClock(String value) {
    final parts = value.split(':');
    if (parts.length != 2) return value;
    final hour = int.tryParse(parts[0]);
    final minute = int.tryParse(parts[1]);
    if (hour == null || minute == null) return value;
    return '${hour.toString().padLeft(2, '0')}:${minute.toString().padLeft(2, '0')}';
  }

  /// 支持 `4-9周`、`1-16周(单)`、`1-16周（双）`、`1-4,6-8周` 等写法。
  static Set<int> parseWeeks(String spec) {
    if (spec.trim().isEmpty) return {};
    final single = spec.contains('单');
    final isDouble = spec.contains('双');
    final main = spec.replaceAll(RegExp(r'[（(][^）)]*[）)]'), '');
    final weeks = <int>{};
    for (final match in RegExp(r'(\d+)(?:\s*-\s*(\d+))?').allMatches(main)) {
      final start = int.tryParse(match.group(1) ?? '');
      if (start == null) continue;
      final endStr = match.group(2);
      final end = (endStr != null && endStr.isNotEmpty)
          ? (int.tryParse(endStr) ?? start)
          : start;
      for (var w = start; w <= end; w++) {
        if (single && w.isEven) continue;
        if (isDouble && w.isOdd) continue;
        weeks.add(w);
      }
    }
    return weeks;
  }

  /// 把公告 HTML 清洗为易读纯文本（保留分段与换行）。
  static String htmlToPlainText(String html) {
    if (html.trim().isEmpty) return '';
    var text = html
        .replaceAll(RegExp(r'<script[\s\S]*?</script>', caseSensitive: false), '')
        .replaceAll(RegExp(r'<style[\s\S]*?</style>', caseSensitive: false), '')
        .replaceAll(RegExp(r'<br\s*/?>', caseSensitive: false), '\n')
        .replaceAll(RegExp(r'</(p|div|li|tr|h[1-6])>', caseSensitive: false), '\n')
        .replaceAll(RegExp(r'<[^>]+>'), '');
    text = decodeEntities(text);
    text = text.replaceAll(RegExp(r'\r\n?'), '\n');
    text = text.replaceAll(RegExp(r'\n{3,}'), '\n\n');
    return text.trim();
  }
}
