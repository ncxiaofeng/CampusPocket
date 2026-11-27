import 'dart:convert';
import 'dart:io';

import '../data/models.dart';
import 'campus_http_client.dart';

/// Open-Meteo 公开天气接口（免 API Key，只发经纬度坐标，不携带任何个人信息）。
class WeatherClient {
  WeatherClient(this._httpClient);

  final CampusHttpClient _httpClient;

  static const WeatherLocation guanglan = WeatherLocation(
    id: 'guanglan',
    name: '南昌 · 广兰校区',
    latitude: 28.718795,
    longitude: 115.825926,
  );

  static const WeatherLocation qinglan = WeatherLocation(
    id: 'qinglan',
    name: '南昌 · 青岚校区',
    latitude: 28.7202,
    longitude: 115.8203,
  );

  static const WeatherLocation fuzhou = WeatherLocation(
    id: 'fuzhou',
    name: '抚州校区',
    latitude: 27.94705,
    longitude: 116.41202,
  );

  static const List<WeatherLocation> presetLocations = [
    guanglan,
    qinglan,
    fuzhou,
  ];

  Future<WeatherSnapshot> fetch([WeatherLocation location = guanglan]) async {
    final uri = Uri.https('api.open-meteo.com', '/v1/forecast', {
      'latitude': location.latitude.toString(),
      'longitude': location.longitude.toString(),
      'current':
          'temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m',
      'hourly': 'temperature_2m,precipitation_probability,weather_code',
      'daily':
          'weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max',
      'timezone': 'Asia/Shanghai',
      'forecast_days': '2',
    });

    final resp = await _httpClient.get(
      uri.toString(),
      headers: const {
        'User-Agent': 'CampusPocket-iOS',
        'Accept': 'application/json',
      },
      useCookies: false,
    );
    if (!resp.isSuccessful) {
      throw HttpException('天气服务返回 HTTP ${resp.statusCode}');
    }
    return parseWeather(resp.body, location);
  }

  static WeatherSnapshot parseWeather(String body, WeatherLocation location) {
    final root = jsonDecode(body);
    if (root is! Map<String, dynamic>) {
      throw const FormatException('天气数据无法解析');
    }
    if (root['error'] == true) {
      throw HttpException('${root['reason'] ?? '天气服务返回错误'}');
    }

    WeatherNow? now;
    final current = root['current'];
    if (current is Map<String, dynamic>) {
      final t = DateTime.tryParse('${current['time'] ?? ''}');
      if (t != null) {
        now = WeatherNow(
          time: t,
          temperature: _toDouble(current['temperature_2m']),
          apparentTemperature: _toDouble(current['apparent_temperature']),
          humidity: _toInt(current['relative_humidity_2m']),
          windSpeed: _toDouble(current['wind_speed_10m']),
          precipitation: _toDouble(current['precipitation']),
          weatherCode: _toInt(current['weather_code']),
        );
      }
    }

    final hourly = <WeatherHourly>[];
    final hourlyRoot = root['hourly'];
    if (hourlyRoot is Map<String, dynamic>) {
      final times = (hourlyRoot['time'] as List?) ?? const [];
      final temps = (hourlyRoot['temperature_2m'] as List?) ?? const [];
      final rains =
          (hourlyRoot['precipitation_probability'] as List?) ?? const [];
      final codes = (hourlyRoot['weather_code'] as List?) ?? const [];
      final count = [times.length, temps.length, codes.length]
          .reduce((a, b) => a < b ? a : b);
      for (var i = 0; i < count; i++) {
        final t = DateTime.tryParse('${times[i] ?? ''}');
        final temp = _tryDouble(temps[i]);
        if (t == null || temp == null) continue;
        hourly.add(
          WeatherHourly(
            time: t,
            temperature: temp,
            precipitationProbability:
                i < rains.length ? _toInt(rains[i]) : 0,
            weatherCode: _toInt(codes[i]),
          ),
        );
      }
    }

    final daily = <WeatherDaily>[];
    final dailyRoot = root['daily'];
    if (dailyRoot is Map<String, dynamic>) {
      final times = (dailyRoot['time'] as List?) ?? const [];
      final codes = (dailyRoot['weather_code'] as List?) ?? const [];
      final maxs = (dailyRoot['temperature_2m_max'] as List?) ?? const [];
      final mins = (dailyRoot['temperature_2m_min'] as List?) ?? const [];
      final rains =
          (dailyRoot['precipitation_probability_max'] as List?) ?? const [];
      final count = [times.length, codes.length, maxs.length, mins.length]
          .reduce((a, b) => a < b ? a : b);
      for (var i = 0; i < count; i++) {
        final d = DateTime.tryParse('${times[i] ?? ''}');
        final mx = _tryDouble(maxs[i]);
        final mn = _tryDouble(mins[i]);
        if (d == null || mx == null || mn == null) continue;
        daily.add(
          WeatherDaily(
            date: d,
            weatherCode: _toInt(codes[i]),
            maxTemperature: mx,
            minTemperature: mn,
            precipitationProbabilityMax:
                i < rains.length ? _toInt(rains[i]) : 0,
          ),
        );
      }
    }

    return WeatherSnapshot(
      location: location,
      now: now,
      hourly: hourly,
      daily: daily,
      fetchedAt: DateTime.now().millisecondsSinceEpoch,
    );
  }

  static String description(int code) => switch (code) {
        0 => '晴',
        1 => '大部晴朗',
        2 => '多云',
        3 => '阴',
        45 || 48 => '有雾',
        51 || 53 || 55 => '毛毛雨',
        56 || 57 || 66 || 67 => '冻雨',
        61 || 63 || 65 => '下雨',
        71 || 73 || 75 || 77 => '下雪',
        80 || 81 || 82 => '阵雨',
        85 || 86 => '阵雪',
        95 => '雷阵雨',
        96 || 99 => '雷暴伴冰雹',
        _ => '多云',
      };

  static double _toDouble(Object? v) => _tryDouble(v) ?? 0.0;

  static double? _tryDouble(Object? v) {
    if (v is num) return v.toDouble();
    return double.tryParse('$v');
  }

  static int _toInt(Object? v) {
    if (v is num) return v.toInt();
    return int.tryParse('$v') ?? 0;
  }
}
