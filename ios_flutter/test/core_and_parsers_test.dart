import 'dart:convert';
import 'dart:io';

import 'package:campuspocket_ios/app_state.dart';
import 'package:campuspocket_ios/core/campus_endpoints.dart';
import 'package:campuspocket_ios/core/coordinate_converter.dart';
import 'package:campuspocket_ios/core/semester_config.dart';
import 'package:campuspocket_ios/data/auth_repository.dart';
import 'package:campuspocket_ios/data/models.dart';
import 'package:campuspocket_ios/data/runtime_config.dart';
import 'package:campuspocket_ios/data/secure_vault.dart';
import 'package:campuspocket_ios/main.dart';
import 'package:campuspocket_ios/network/campus_cookie_jar.dart';
import 'package:campuspocket_ios/network/cas_auto_login.dart';
import 'package:campuspocket_ios/network/cas_sso_manager.dart';
import 'package:campuspocket_ios/network/chaoxing_client.dart';
import 'package:campuspocket_ios/network/electricity_client.dart';
import 'package:campuspocket_ios/network/huilife_client.dart';
import 'package:campuspocket_ios/network/jw_adapter.dart';
import 'package:campuspocket_ios/network/jw_parser.dart';
import 'package:campuspocket_ios/network/library_seat_client.dart';
import 'package:campuspocket_ios/network/ujing_client.dart';
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

  group('真机回归：零信任二次验证防秒退', () {
    test('跳转到新设备验证码/MFA 页时绝不自动退出，仅落到 shortcut.html 才自动完成', () {
      expect(
        CampusEndpoints.atrustLogin
            .shouldAutoCompleteUrl('https://atrust1.ecut.edu.cn/portal/sms-verify'),
        isFalse,
      );
      expect(
        CampusEndpoints.atrustLogin
            .shouldAutoCompleteUrl('https://atrust1.ecut.edu.cn/passport/v1/mfa/device'),
        isFalse,
      );
      expect(
        CampusEndpoints.atrustLogin
            .shouldAutoCompleteUrl('https://atrust1.ecut.edu.cn/portal/index.html'),
        isFalse,
      );
      expect(
        CampusEndpoints.atrustLogin
            .shouldAutoCompleteUrl('https://atrust1.ecut.edu.cn/portal/shortcut.html'),
        isTrue,
      );
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

  group('AuthRepository & CasSsoManager & CasAutoLogin', () {
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

    test('CasAutoLogin 表单解析与 AES-128-CBC 密码加密确定性校验', () {
      const html = '''
      <form id="casLoginForm" action="/authserver/login">
        <input type="hidden" name="execution" value="e1s1" />
        <input type="hidden" id="pwdDefaultEncryptSalt" value="1234567890abcdef" />
      </form>
      ''';
      final form = CasAutoLogin.parseLoginForm(html);
      expect(form.action, '/authserver/login');
      expect(form.encryptSalt, '1234567890abcdef');
      expect(form.hiddenFields['execution'], 'e1s1');

      final enc1 = CasAutoLogin.encryptCasPassword(
        'secret',
        '1234567890abcdef',
        fixedPrefix: 'A' * 64,
        fixedIv: 'fedcba0987654321',
      );
      final enc2 = CasAutoLogin.encryptCasPassword(
        'secret',
        '1234567890abcdef',
        fixedPrefix: 'A' * 64,
        fixedIv: 'fedcba0987654321',
      );
      expect(enc1, enc2);
      expect(base64Decode(enc1).length % 16, 0);
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

      final failGrade = JwAdapter.parseGradeMap({
        'kcmc': '高等数学',
        'bfzcj': '54',
        'xf': '4.0',
        'jd': '0.0',
        'xnmmc': '2025-2026',
        'xqmmc': '1',
      });
      expect(failGrade!.passed, isFalse);
    });
  });

  group('高级配置与一次性远程安全配置码', () {
    test('解析 key=value 与 JSON 短键并校验远程白名单防 SSRF', () {
      final m1 = RuntimeConfigTextParser.parse(
        'electricity.operator.pwd=pwd1\nujing.hmac.key=key2\nhuilife.app.sign.salt=salt3',
      );
      expect(m1[RuntimeConfigTextParser.keyElectricity], 'pwd1');
      expect(m1[RuntimeConfigTextParser.keyUjing], 'key2');
      expect(m1[RuntimeConfigTextParser.keyHuiLife], 'salt3');

      final m2 = RuntimeConfigTextParser.parse('{"e":"abc","h":"xyz"}');
      expect(m2[RuntimeConfigTextParser.keyElectricity], 'abc');
      expect(m2[RuntimeConfigTextParser.keyHuiLife], 'xyz');

      // 合法腾讯云 COS /claim/ 预签名
      final okClaim = RemoteConfigClaimParser.parse(jsonEncode({
        'cp_claim': 1,
        'm': 'once',
        'g': 'https://bucket-123.cos.ap-shanghai.myqcloud.com/claim/abc.json?q-signature=0123456789abcdef0123456789abcdef01234567',
        'ds': 'abcdef0123456789abcdef0123456789abcdef01',
      }));
      expect(okClaim, isNotNull);
      expect(okClaim!.mode, RemoteClaimMode.once);
      expect(okClaim.deleteUrl, contains('abcdef0123456789abcdef0123456789abcdef01'));

      // 恶意 HTTP 或非白名单域名必须拦截
      expect(
        RemoteConfigClaimParser.parse(jsonEncode({
          'cp_claim': 1,
          'g': 'http://172.20.130.13/claim/a?q-signature=123',
        })),
        isNull,
      );
      expect(
        RemoteConfigClaimParser.parse(jsonEncode({
          'cp_claim': 1,
          'g': 'https://evil.com/claim/a?q-signature=123',
        })),
        isNull,
      );
    });
  });

  group('电费 / U净 / 坐标纠偏 / 慧生活798 / 学习通', () {
    test('电费补零日期与 0 度幽灵行过滤', () {
      expect(ElectricityClient.formatQueryDate(DateTime(2026, 11, 1)), '2026-11-01');
      expect(ElectricityClient.extractUserId('var userid="2023001234";'), '2023001234');
      final list = ElectricityClient.parseDosageRangeJson('''
        {"ret":"1","data":{"list":[
          {"data_date":"2026-11-1","energy_used":"4.25"},
          {"data_date":"2026-11-2","energy_used":"0.0"}
        ]}}
      ''');
      expect(list.length, 1);
      expect(list.first.date, '2026-11-01');
      expect(list.first.kwh, 4.25);
    });

    test('WGS-84 转 GCJ-02 在南昌区域有明显纠偏且海外不偏移', () {
      final (glLat, glLng) = CoordinateConverter.wgs84ToGcj02(28.718795, 115.825926);
      expect((glLat - 28.718795).abs() + (glLng - 115.825926).abs(), greaterThan(0.001));
      final (usLat, usLng) = CoordinateConverter.wgs84ToGcj02(37.7749, -122.4194);
      expect(usLat, 37.7749);
      expect(usLng, -122.4194);
    });

    test('U净 JWT 提取与校验', () {
      const sample = 'eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.abcdefghij123456';
      expect(UjingTokenExtractor.extractFromJsResult('"$sample"'), sample);
      expect(UjingTokenExtractor.extractFromJsResult('null'), isNull);
      expect(UjingTokenExtractor.extractFromJsResult('"invalid"'), isNull);
    });

    test('慧生活798 二维码解析、MD5 签名与本人安全停止守卫', () {
      expect(HuiLifeDeviceIdParser.parse('12345678').deviceId, '12345678');
      expect(
        HuiLifeDeviceIdParser.parse('ilife798s://i/device/nav?id=abc123456789').deviceId,
        'abc123456789',
      );
      expect(
        HuiLifeDeviceIdParser.parse('https://i.ilife798.com/q/1/b?id=QR9988').qrId,
        'QR9988',
      );
      expect(
        HuiLifeClient.signAppLogin('13800138000', '123456', 'TEST_SALT').length,
        32,
      );

      // 他人正在使用的设备绝不允许停止
      const othersRunning = HuiLifeDeviceStatus(
        deviceStatus: 1,
        geneStatus: 2,
        usingUserId: 'other_user',
      );
      expect(
        HuiLifeClient.canSafelyStop(
          status: othersRunning,
          currentUid: 'my_uid',
          localStartedAtMillis: null,
        ),
        isFalse,
      );
      // 本机 5 分钟前启动的设备允许停止
      final now = DateTime.now().millisecondsSinceEpoch;
      expect(
        HuiLifeClient.canSafelyStop(
          status: othersRunning,
          currentUid: 'my_uid',
          localStartedAtMillis: now - 5 * 60 * 1000,
          nowMillis: now,
        ),
        isTrue,
      );
    });

    test('学习通 HTML 作业解析、学期排序与官方 UA', () {
      const course = ChaoxingCourse(
        courseId: '101',
        classId: '202',
        name: '操作系统',
        className: '2026-2027-1',
        teacher: '李老师',
        termRank: ChaoxingTerm.current,
      );
      const html = '''
        <dt data="/work/do?id=1" status="0">
          <h3>实验一</h3>
          <span class="status">待完成</span>
          <p>截止 12-01</p>
        </dt>
        <dt data="/work/do?id=2" status="1">
          <h3>实验二</h3>
          <span class="status">待批阅</span>
        </dt>
      ''';
      final works = ChaoxingHtmlParser.parseWorkList(html, course);
      expect(works.length, 2);
      expect(works[0].needsAction, isTrue);
      expect(works[1].needsAction, isFalse);
      expect(ChaoxingTerm.rank('2026-2027 第一学期', DateTime(2026, 11, 27)), ChaoxingTerm.current);
      expect(ChaoxingUserAgent.build('abc123'), contains('schild:'));
    });
  });

  group('LibrarySeatClient / WeatherClient / UpdateChecker', () {
    test('解析图书馆座位树、天气与 IPA 更新', () {
      final snap = LibrarySeatClient.parseSnapshot(
        '{"code":0,"data":{"date":["2026-11-27"],"list":[{"id":"1","name":"主馆","free_num":50,"total_num":100,"sort":1,"children":[]}]}}',
      );
      expect(snap.campuses.first.free, 50);

      final w = WeatherClient.parseWeather(
        '{"current":{"time":"2026-11-27T14:00","temperature_2m":18.6,"apparent_temperature":17.5,"relative_humidity_2m":60,"wind_speed_10m":8.2,"precipitation":0.0,"weather_code":2},"hourly":{"time":[],"temperature_2m":[],"weather_code":[]},"daily":{"time":[],"weather_code":[],"temperature_2m_max":[],"temperature_2m_min":[]}}',
        WeatherClient.guanglan,
      );
      expect(WeatherClient.description(w.now!.weatherCode), '多云');

      expect(UpdateChecker.isNewer('0.6.10', '0.6.5'), isTrue);
    });
  });

  group('iOS 免开发者账号自签合规检查', () {
    test('Info.plist 满足校园内网、离线扫码与自签安全要求且无付费 Entitlements', () {
      final plistFile = File('ios/Runner/Info.plist');
      expect(plistFile.existsSync(), isTrue);
      final content = plistFile.readAsStringSync();
      expect(content, contains('<string>冻小梨</string>'));
      expect(content, contains('NSLocalNetworkUsageDescription'));
      expect(content, contains('NSCameraUsageDescription'));
      expect(content, contains('NSAllowsArbitraryLoads'));
      expect(content, contains('NSAllowsLocalNetworking'));
      expect(content, contains('UIFileSharingEnabled'));
      expect(content, isNot(contains('aps-environment')));
      expect(File('ios/Runner/Runner.entitlements').existsSync(), isFalse);
    });
  });

  group('Widget 渲染烟雾测试', () {
    testWidgets('首页与底栏四 Tab 正常渲染并包含完整服务入口', (tester) async {
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
      expect(find.text('自动登录（可选）'), findsOneWidget);
      expect(find.text('高级配置（电费 / 798 设备盐）'), findsOneWidget);
    });
  });
}
