import 'dart:io';

import 'package:campuspocket_ios/app_state.dart';
import 'package:campuspocket_ios/core/campus_endpoints.dart';
import 'package:campuspocket_ios/core/semester_config.dart';
import 'package:campuspocket_ios/data/auth_repository.dart';
import 'package:campuspocket_ios/data/models.dart';
import 'package:campuspocket_ios/data/secure_vault.dart';
import 'package:campuspocket_ios/main.dart';
import 'package:campuspocket_ios/network/campus_cookie_jar.dart';
import 'package:campuspocket_ios/network/cas_sso_manager.dart';
import 'package:campuspocket_ios/network/jw_adapter.dart';
import 'package:campuspocket_ios/network/jw_parser.dart';
import 'package:campuspocket_ios/network/library_seat_client.dart';
import 'package:campuspocket_ios/network/update_checker.dart';
import 'package:campuspocket_ios/network/weather_client.dart';
import 'package:campuspocket_ios/ui/theme/glass_theme.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('SemesterConfig', () {
    test('2026-10-24 为第 7 周，2026-09-07 为第 1 周', () {
      expect(SemesterConfig.weekFor(DateTime(2026, 9, 7)), 1);
      expect(SemesterConfig.weekFor(DateTime(2026, 10, 24)), 7);
      expect(SemesterConfig.weekFor(DateTime(2026, 8, 1)), 1);
    });
  });

  group('CampusCookieJar', () {
    test('正确隔离 /sso 与 /jwglxt 两个同名 JSESSIONID', () {
      final now = DateTime.now().millisecondsSinceEpoch;
      final rows = [
        CookieRecord(
          domain: '172.20.130.13',
          name: 'JSESSIONID',
          value: 'SSO_OLD',
          path: '/sso',
          updatedAt: now,
        ),
        CookieRecord(
          domain: '172.20.130.13',
          name: 'JSESSIONID',
          value: 'JW_REAL',
          path: '/jwglxt',
          updatedAt: now,
        ),
      ];

      expect(CampusCookieJar.pathsOverlap('/sso', '/jwglxt'), isFalse);
      expect(CampusCookieJar.pathsOverlap('/authserver', '/'), isTrue);

      final jwSelected = CampusCookieJar.selectCookies(
        rows,
        '172.20.130.13',
        '/jwglxt/kbcx/xskbcx_cxXsgrkb.html',
        now,
      );
      expect(jwSelected.length, 1);
      expect(jwSelected.first.value, 'JW_REAL');
    });

    test('过滤过期 Cookie 且拒绝跨域名泄露', () {
      final now = DateTime.now().millisecondsSinceEpoch;
      final rows = [
        CookieRecord(
          domain: 'authserver.ecut.edu.cn',
          name: 'CASTGC',
          value: 'EXPIRED',
          path: '/authserver',
          expiresAt: now - 1000,
          updatedAt: now - 2000,
        ),
        CookieRecord(
          domain: 'evil-ecut.edu.cn',
          name: 'CASTGC',
          value: 'BAD',
          path: '/',
          updatedAt: now,
        ),
      ];
      final res = CampusCookieJar.selectCookies(
        rows,
        'authserver.ecut.edu.cn',
        '/authserver/login',
        now,
      );
      expect(res, isEmpty);
    });
  });

  group('AuthRepository & CasSsoManager', () {
    test('CAS 主凭据整套保留 route，从代理域镜像时保留已有 route', () async {
      final repo = AuthRepository(InMemorySecureVault());
      await repo.saveCasMasterCookies(
        'CASTGC=TGT-111; route=node01; JSESSIONID=ABC',
        fromCasHost: true,
      );
      var casCookies = await repo.loadCookiesForHost(CampusEndpoints.casHost);
      final map1 = {for (final c in casCookies) c.name: c.value};
      expect(map1['CASTGC'], 'TGT-111');
      expect(map1['route'], 'node01');

      // 从零信任代理域镜像新 CASTGC，不得把原有 route 覆盖丢失
      await repo.saveCasMasterCookies(
        'CASTGC=TGT-222; proxy_only=should_ignore',
        fromCasHost: false,
      );
      casCookies = await repo.loadCookiesForHost(CampusEndpoints.casHost);
      final map2 = {for (final c in casCookies) c.name: c.value};
      expect(map2['CASTGC'], 'TGT-222');
      expect(map2['route'], 'node01');
      expect(map2.containsKey('proxy_only'), isFalse);
    });

    test('CasSsoManager 解析 CASTGC 并正确编码 service URL', () {
      expect(
        CasSsoManager.casTicketValue('route=1; CASTGC=TGT-XYZ; foo=bar'),
        'TGT-XYZ',
      );
      expect(CasSsoManager.hasCasTicket('JSESSIONID=123'), isFalse);
      final url =
          CasSsoManager.buildServiceLoginUrl('https://ehall.ecut.edu.cn/a?b=1');
      expect(
        url,
        contains('service=https%3A%2F%2Fehall.ecut.edu.cn%2Fa%3Fb%3D1'),
      );
    });
  });

  group('JwParser & JwAdapter', () {
    test('解析单双周、多区间周次与节次', () {
      expect(JwParser.parseWeeks('1-6周(单)'), {1, 3, 5});
      expect(JwParser.parseWeeks('1-6周（双）'), {2, 4, 6});
      expect(JwParser.parseWeeks('1-3,5-6周'), {1, 2, 3, 5, 6});
      expect(JwParser.parsePeriods('3-4节'), (3, 4));
      expect(JwParser.parsePeriods('9'), (9, 9));
    });

    test('解析考试日期时间多种格式', () {
      expect(
        JwParser.parseExamTime('2026-01-10(09:00-11:00)'),
        ('2026-01-10', '09:00', '11:00'),
      );
      expect(
        JwParser.parseExamTime('2026年1月4日 9:00～11:30'),
        ('2026-01-04', '09:00', '11:30'),
      );
    });

    test('解析课表与成绩 Map（含不及格标红）', () {
      final course = JwAdapter.parseCourseMap({
        'kcmc': '数据结构',
        'xm': '张老师',
        'cdmc': '广兰-3102',
        'xqmc': '广兰校区',
        'xqj': '2',
        'jc': '3-4节',
        'zcd': '1-16周',
        'kch': 'CS201',
      });
      expect(course, isNotNull);
      expect(course!.dayOfWeek, 1);
      expect(course.startPeriod, 3);
      expect(course.endPeriod, 4);
      expect(course.weeks.contains(8), isTrue);

      final failGrade = JwAdapter.parseGradeMap({
        'kcmc': '高等数学',
        'bfzcj': '54',
        'xf': '4.0',
        'jd': '0.0',
        'xnmmc': '2025-2026',
        'xqmmc': '1',
      });
      expect(failGrade, isNotNull);
      expect(failGrade!.passed, isFalse);
    });

    test('清洗公告 HTML 为纯文本', () {
      final plain = JwParser.htmlToPlainText(
        '<p>第一段&nbsp;内容</p><br/><p>第二段 &amp; 结束</p>',
      );
      expect(plain, contains('第一段 内容'));
      expect(plain, contains('第二段 & 结束'));
    });
  });

  group('LibrarySeatClient / WeatherClient / UpdateChecker', () {
    test('解析图书馆座位树', () {
      const json = '''
      {
        "code": 0,
        "data": {
          "date": ["2026-11-27"],
          "list": [
            {
              "id": "1",
              "name": "主校区图书馆",
              "free_num": 120,
              "total_num": 300,
              "sort": 1,
              "children": [
                {"id": "11", "name": "二楼东区", "free_num": 45, "total_num": 100, "sort": 1, "children": []}
              ]
            }
          ]
        }
      }
      ''';
      final snap = LibrarySeatClient.parseSnapshot(json);
      expect(snap.dates, ['2026-11-27']);
      expect(snap.campuses.first.free, 120);
      expect(snap.campuses.first.children.first.name, '二楼东区');
    });

    test('解析 Open-Meteo 天气与 WMO 描述', () {
      const json = '''
      {
        "current": {
          "time": "2026-11-27T14:00",
          "temperature_2m": 18.6,
          "apparent_temperature": 17.5,
          "relative_humidity_2m": 60,
          "wind_speed_10m": 8.2,
          "precipitation": 0.0,
          "weather_code": 2
        },
        "hourly": {
          "time": ["2026-11-27T14:00"],
          "temperature_2m": [18.6],
          "precipitation_probability": [20],
          "weather_code": [2]
        },
        "daily": {
          "time": ["2026-11-27"],
          "weather_code": [2],
          "temperature_2m_max": [21.0],
          "temperature_2m_min": [12.0],
          "precipitation_probability_max": [30]
        }
      }
      ''';
      final snap = WeatherClient.parseWeather(json, WeatherClient.guanglan);
      expect(snap.now?.temperature, 18.6);
      expect(WeatherClient.description(snap.now!.weatherCode), '多云');
      expect(snap.daily.first.precipitationProbabilityMax, 30);
    });

    test('UpdateChecker 正确比较 ios-v 版本并提取 .ipa 下载地址', () {
      expect(UpdateChecker.normalizeVersion('ios-v0.6.5'), '0.6.5');
      expect(UpdateChecker.isNewer('0.6.10', '0.6.4'), isTrue);
      expect(UpdateChecker.isNewer('0.6.4', '0.6.4'), isFalse);

      const releasesJson = '''
      [
        {
          "tag_name": "v0.6.9",
          "draft": false,
          "html_url": "https://example.com/android",
          "body": "Android only",
          "assets": [
            {"name": "CampusPocket-0.6.9.apk", "browser_download_url": "https://example.com/a.apk"}
          ]
        },
        {
          "tag_name": "ios-v0.6.5",
          "draft": false,
          "html_url": "https://example.com/ios",
          "body": "iOS 自签版更新",
          "assets": [
            {"name": "CampusPocket-iOS-0.6.5-unsigned.ipa", "browser_download_url": "https://example.com/app.ipa"}
          ]
        }
      ]
      ''';
      final update = UpdateChecker.parseLatestIpaRelease(releasesJson, '0.6.4');
      expect(update, isNotNull);
      expect(update!.version, '0.6.5');
      expect(update.ipaDownloadUrl, 'https://example.com/app.ipa');
    });
  });

  group('iOS 免开发者账号自签合规检查', () {
    test('Info.plist 满足校园内网与自签安全要求且无付费 Entitlements', () {
      final plistFile = File('ios/Runner/Info.plist');
      expect(plistFile.existsSync(), isTrue);
      final content = plistFile.readAsStringSync();
      expect(content, contains('<string>冻小梨</string>'));
      expect(content, contains('NSLocalNetworkUsageDescription'));
      expect(content, contains('NSAllowsArbitraryLoads'));
      expect(content, contains('NSAllowsLocalNetworking'));
      expect(content, contains('UIFileSharingEnabled'));
      expect(content, isNot(contains('aps-environment')));

      // 不应包含需要付费开发者权限的 entitlements 文件
      final entitlementsFile = File('ios/Runner/Runner.entitlements');
      expect(entitlementsFile.existsSync(), isFalse);
    });
  });

  group('Widget 渲染烟雾测试', () {
    testWidgets('首页与底栏四 Tab 正常渲染与切换', (tester) async {
      final state = CampusAppState(vault: InMemorySecureVault());
      await tester.pumpWidget(CampusPocketIosApp(appState: state));
      await tester.pump();

      final bottomBar = find.byType(LiquidGlassBottomBar);
      expect(find.descendant(of: bottomBar, matching: find.text('首页')), findsOneWidget);
      expect(find.descendant(of: bottomBar, matching: find.text('课表')), findsOneWidget);
      expect(find.descendant(of: bottomBar, matching: find.text('校园卡')), findsOneWidget);
      expect(find.descendant(of: bottomBar, matching: find.text('更多')), findsOneWidget);

      await tester.tap(find.descendant(of: bottomBar, matching: find.text('更多')));
      await tester.pump();
      expect(find.text('统一身份认证'), findsOneWidget);
      expect(find.text('零信任 WebVPN 门户（校外）'), findsOneWidget);
    });
  });
}
