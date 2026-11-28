import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../core/class_times.dart';
import '../../core/schedule_adjustments.dart';
import '../../data/models.dart';
import '../../network/campus_network_status.dart';
import '../../network/electricity_client.dart';
import '../../network/weather_client.dart';
import '../theme/glass_theme.dart';

/// 首页：今日课程、校园天气、电费卡片（配置时显示）、学习通待办、慧生活798、校园卡与快捷服务。
class TodayScreen extends StatelessWidget {
  final CampusAppState appState;
  final VoidCallback onOpenLogin;
  final VoidCallback onOpenPortal;
  final VoidCallback onOpenSchedule;
  final VoidCallback onOpenCard;
  final VoidCallback onOpenGrades;
  final VoidCallback onOpenExams;
  final VoidCallback onOpenClassrooms;
  final VoidCallback onOpenNotices;
  final VoidCallback onOpenLibrary;
  final VoidCallback onOpenElectricity;
  final VoidCallback onOpenUjing;
  final VoidCallback onOpenHuiLife;
  final VoidCallback onOpenChaoxing;
  final VoidCallback onSync;

  const TodayScreen({
    super.key,
    required this.appState,
    required this.onOpenLogin,
    required this.onOpenPortal,
    required this.onOpenSchedule,
    required this.onOpenCard,
    required this.onOpenGrades,
    required this.onOpenExams,
    required this.onOpenClassrooms,
    required this.onOpenNotices,
    required this.onOpenLibrary,
    required this.onOpenElectricity,
    required this.onOpenUjing,
    required this.onOpenHuiLife,
    required this.onOpenChaoxing,
    required this.onSync,
  });

  static const _weekdays = ['一', '二', '三', '四', '五', '六', '日'];

  String _greeting(DateTime now) {
    if (now.hour >= 5 && now.hour <= 10) return '早上好，同学';
    if (now.hour >= 11 && now.hour <= 13) return '中午好，同学';
    if (now.hour >= 14 && now.hour <= 18) return '下午好，同学';
    return '晚上好，同学';
  }

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final todayIso = ScheduleAdjustments.formatIso(now);
    final week = appState.currentWeek;
    final adjustmentsByDate = appState.scheduleAdjustmentsByDate;
    final todayAdjustment = adjustmentsByDate[todayIso];
    final todayCourses = ScheduleAdjustments.resolveDayCourses(
      appState.courses,
      now,
      adjustmentsByDate,
    ).map((r) => r.course).toList()
      ..sort((a, b) => a.startPeriod.compareTo(b.startPeriod));
    final todayExams = appState.exams
        .where((e) => e.examDate == todayIso)
        .toList()
      ..sort((a, b) => a.startClock.compareTo(b.startClock));

    return RefreshIndicator(
      onRefresh: () async {
        await appState.refreshNetwork();
        await appState.refreshWeather(force: true);
        if (appState.hasCasMaster) {
          await appState.refreshCard(silent: true);
          if (appState.runtimeConfig.hasElectricity) {
            await appState.refreshElectricity(silent: true);
          }
        }
        if (appState.hasChaoxingSession) {
          await appState.refreshChaoxing(silent: true);
        }
        if (appState.hasHuiLifeAccount || appState.hasHuiLifeApp) {
          await appState.refreshHuiLife(silent: true);
        }
      },
      child: ListView(
        padding: const EdgeInsets.fromLTRB(
          16,
          12,
          16,
          LiquidGlassBottomBar.contentBottomInset,
        ),
        children: [
          _buildHeader(context, now, week),
          const SizedBox(height: 12),
          if (appState.sessionExpired) ...[
            _AlertBanner(
              color: GlassColors.danger,
              icon: Icons.error_outline_rounded,
              text: appState.sessionExpiredMessage ?? '统一认证已过期，请重新登录',
              actionLabel: '去登录',
              onAction: onOpenLogin,
            ),
            const SizedBox(height: 10),
          ],
          if (appState.netState == CampusNetworkState.offCampus) ...[
            _AlertBanner(
              color: appState.hasProxySession
                  ? GlassColors.primary
                  : GlassColors.warning,
              icon: appState.hasProxySession
                  ? Icons.verified_user_outlined
                  : Icons.wifi_off_rounded,
              text: appState.hasProxySession
                  ? '当前在校外 · 已就绪 WebVPN 教务代理，可直接同步'
                  : '未检测到校园网内网（如已连校园网请先允许“本地网络”权限并点右上角重测）',
              actionLabel: '零信任门户',
              onAction: onOpenPortal,
            ),
            const SizedBox(height: 10),
          ],
          _buildTodayCoursesCard(
            context,
            todayCourses,
            todayExams,
            week,
            todayAdjustment,
          ),
          const SizedBox(height: 14),
          _buildWeatherCard(context),
          if (appState.runtimeConfig.hasElectricity) ...[
            const SizedBox(height: 14),
            _buildElectricityCard(context),
          ],
          const SizedBox(height: 14),
          _buildChaoxingCard(context),
          const SizedBox(height: 14),
          _buildHuiLifeCard(context),
          const SizedBox(height: 14),
          _buildCampusCardSummary(context),
          const SizedBox(height: 14),
          _buildQuickServicesGrid(context),
        ],
      ),
    );
  }

  Widget _buildHeader(BuildContext context, DateTime now, int week) {
    final netChipColor = switch (appState.netState) {
      CampusNetworkState.onCampus => GlassColors.primary,
      CampusNetworkState.offCampus =>
        appState.hasProxySession ? Colors.blue : GlassColors.warning,
      _ => GlassColors.muted(context),
    };
    final netLabel = switch (appState.netState) {
      CampusNetworkState.onCampus => '校园网',
      CampusNetworkState.offCampus =>
        appState.hasProxySession ? 'WebVPN 代理' : '点此重测校园网',
      CampusNetworkState.checking => '检测中',
      CampusNetworkState.unknown => '检测网络',
    };

    return Row(
      crossAxisAlignment: CrossAxisAlignment.center,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                _greeting(now),
                style: TextStyle(
                  fontSize: 24,
                  fontWeight: FontWeight.w800,
                  color: GlassColors.ink(context),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                '第 $week 周 · 周${_weekdays[now.weekday - 1]} · ${now.month}月${now.day}日',
                style: TextStyle(
                  fontSize: 13,
                  color: GlassColors.muted(context),
                ),
              ),
            ],
          ),
        ),
        GestureDetector(
          onTap: () => appState.refreshNetwork(),
          child: Container(
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
            decoration: BoxDecoration(
              color: netChipColor.withValues(alpha: 0.14),
              borderRadius: BorderRadius.circular(20),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(Icons.circle, size: 8, color: netChipColor),
                const SizedBox(width: 6),
                Text(
                  netLabel,
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w600,
                    color: netChipColor,
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildTodayCoursesCard(
    BuildContext context,
    List<JwCourse> todayCourses,
    List<JwExam> todayExams,
    int week,
    ScheduleDayAdjustment? todayAdjustment,
  ) {
    final hasAnyToday = todayCourses.isNotEmpty || todayExams.isNotEmpty;
    final adjNote = todayAdjustment == null
        ? ''
        : todayAdjustment.type == ScheduleAdjustmentType.rest
            ? ' · ${todayAdjustment.label.isEmpty ? '放假停课' : todayAdjustment.label}'
            : ' · 调休上 ${ScheduleAdjustments.shortDateWithWeekday(todayAdjustment.sourceDate)} 课';
    final summaryText = !hasAnyToday
        ? (todayAdjustment?.type == ScheduleAdjustmentType.rest
            ? '今日放假停课$adjNote'
            : '第 $week 周今天暂无安排')
        : todayExams.isEmpty
            ? '共 ${todayCourses.length} 节课程$adjNote'
            : todayCourses.isEmpty
                ? '今天有 ${todayExams.length} 场考试$adjNote'
                : '${todayCourses.length} 节课程 · ${todayExams.length} 场考试$adjNote';
    return GlassCard(
      onTap: onOpenSchedule,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              IconBadge(
                icon: Icons.calendar_today_rounded,
                color: todayExams.isNotEmpty
                    ? const Color(0xFFD93848)
                    : const Color(0xFF3B82F6),
                size: 36,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      todayExams.isNotEmpty ? '今日课程与考试' : '今日课程',
                      style: TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w700,
                        color: GlassColors.ink(context),
                      ),
                    ),
                    Text(
                      summaryText,
                      style: TextStyle(
                        fontSize: 12,
                        color: todayExams.isNotEmpty
                            ? const Color(0xFFD93848)
                            : GlassColors.muted(context),
                      ),
                    ),
                  ],
                ),
              ),
              TextButton.icon(
                onPressed: appState.hasCasMaster ? onSync : onOpenLogin,
                icon: appState.syncingJw
                    ? const SizedBox(
                        width: 14,
                        height: 14,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : Icon(
                        appState.hasCasMaster
                            ? Icons.sync_rounded
                            : Icons.login_rounded,
                        size: 16,
                      ),
                label: Text(appState.hasCasMaster ? '同步' : '登录'),
              ),
            ],
          ),
          if (!hasAnyToday) ...[
            const SizedBox(height: 12),
            Text(
              appState.courses.isEmpty
                  ? '完成统一身份认证并同步课表后，这里会自动展示今日课程与上下课时间。'
                  : '今天没有课，去图书馆或自习室看看空位吧。',
              style: TextStyle(fontSize: 13, color: GlassColors.muted(context)),
            ),
          ] else ...[
            const SizedBox(height: 12),
            for (final exam in todayExams)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: _TodayExamTile(exam: exam),
              ),
            for (final course in todayCourses)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: _TodayCourseTile(course: course),
              ),
          ],
        ],
      ),
    );
  }

  Widget _buildWeatherCard(BuildContext context) {
    final snap = appState.weather;
    final now = snap?.now;
    final todayDaily =
        (snap != null && snap.daily.isNotEmpty) ? snap.daily.first : null;

    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const IconBadge(
                icon: Icons.wb_sunny_outlined,
                color: Color(0xFFF59E0B),
                size: 36,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      snap?.location.name ?? '校园天气',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: GlassColors.ink(context),
                      ),
                    ),
                    Text(
                      now != null
                          ? '${WeatherClient.description(now.weatherCode)} · 体感 ${now.apparentTemperature.round()}° · 湿度 ${now.humidity}%'
                          : (appState.weatherError ?? 'Open-Meteo 逐小时模型预报'),
                      style: TextStyle(
                        fontSize: 12,
                        color: GlassColors.muted(context),
                      ),
                    ),
                  ],
                ),
              ),
              if (now != null)
                Text(
                  '${now.temperature.round()}°',
                  style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w800,
                    color: GlassColors.ink(context),
                  ),
                ),
            ],
          ),
          if (todayDaily != null) ...[
            const SizedBox(height: 10),
            Text(
              '今日 ${todayDaily.minTemperature.round()}° ~ ${todayDaily.maxTemperature.round()}° · 降水概率 ${todayDaily.precipitationProbabilityMax}%',
              style: TextStyle(
                fontSize: 12.5,
                color: GlassColors.muted(context),
              ),
            ),
          ],
          const SizedBox(height: 10),
          Wrap(
            spacing: 8,
            runSpacing: 6,
            children: [
              for (final opt in const [
                ('auto', '当前位置'),
                ('guanglan', '广兰'),
                ('qinglan', '青岚'),
                ('fuzhou', '抚州'),
              ])
                ChoiceChip(
                  label: Text(opt.$2, style: const TextStyle(fontSize: 12)),
                  selected: appState.weatherLocationPref == opt.$1,
                  onSelected: (_) => appState.setWeatherLocationPref(opt.$1),
                  visualDensity: VisualDensity.compact,
                ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildElectricityCard(BuildContext context) {
    final snap = appState.electricity;
    final today = DateTime.now();
    final yKey = ElectricityClient.formatQueryDate(
      DateTime(today.year, today.month, today.day)
          .subtract(const Duration(days: 1)),
    );
    final yesterday = snap?.dailyUsage
        .where((e) => e.date == yKey)
        .map((e) => e.kwh)
        .firstOrNull;

    return GlassCard(
      onTap: onOpenElectricity,
      child: Row(
        children: [
          const IconBadge(
            icon: Icons.bolt_rounded,
            color: Color(0xFFF59E0B),
            size: 38,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '宿舍电费',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: GlassColors.ink(context),
                  ),
                ),
                Text(
                  yesterday != null
                      ? '昨日用电 ${yesterday.toStringAsFixed(2)} 度'
                      : '点击查看余额与 30 天柱状图',
                  style: TextStyle(
                    fontSize: 12,
                    color: GlassColors.muted(context),
                  ),
                ),
              ],
            ),
          ),
          Text(
            snap?.balance != null ? '¥${snap!.balance!.toStringAsFixed(2)}' : '--',
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.w800,
              color: Color(0xFFF59E0B),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildChaoxingCard(BuildContext context) {
    final snap = appState.chaoxing;
    final pendingWorks = snap?.pendingWorkCount ?? 0;
    final pendingExams = snap?.pendingExamCount ?? 0;
    final signs = snap?.signs.length ?? 0;
    return GlassCard(
      onTap: onOpenChaoxing,
      child: Row(
        children: [
          const IconBadge(
            icon: Icons.assignment_outlined,
            color: Color(0xFF6366F1),
            size: 38,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '学习通待办',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: GlassColors.ink(context),
                  ),
                ),
                Text(
                  !appState.hasChaoxingSession
                      ? '点击登录查看签到、作业与考试'
                      : '签到 $signs · 待办作业 $pendingWorks · 考试 $pendingExams',
                  style: TextStyle(
                    fontSize: 12,
                    color: GlassColors.muted(context),
                  ),
                ),
              ],
            ),
          ),
          if (signs > 0)
            Chip(
              label: Text('$signs 签到', style: const TextStyle(fontSize: 11)),
              backgroundColor: GlassColors.danger.withValues(alpha: 0.16),
              visualDensity: VisualDensity.compact,
            )
          else
            const Icon(Icons.chevron_right_rounded),
        ],
      ),
    );
  }

  Widget _buildHuiLifeCard(BuildContext context) {
    final wallet = appState.huiLifeWallet;
    final onlineCount =
        appState.huiLifeStatuses.values.where((s) => s.canStart).length;
    return GlassCard(
      onTap: onOpenHuiLife,
      child: Row(
        children: [
          const IconBadge(
            icon: Icons.water_drop_outlined,
            color: Color(0xFF0EA5E9),
            size: 38,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '慧生活798',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: GlassColors.ink(context),
                  ),
                ),
                Text(
                  wallet == null
                      ? '免配置查钱包余额与设备状态'
                      : '设备 ${appState.huiLifeDevices.length} 台 · 在线 $onlineCount 台',
                  style: TextStyle(
                    fontSize: 12,
                    color: GlassColors.muted(context),
                  ),
                ),
              ],
            ),
          ),
          if (wallet != null)
            Text(
              '¥${wallet.balance.toStringAsFixed(2)}',
              style: const TextStyle(
                fontSize: 20,
                fontWeight: FontWeight.w800,
                color: Color(0xFF0EA5E9),
              ),
            )
          else
            const Icon(Icons.chevron_right_rounded),
        ],
      ),
    );
  }

  Widget _buildCampusCardSummary(BuildContext context) {
    final overview = appState.cardOverview;
    return GlassCard(
      onTap: onOpenCard,
      child: Row(
        children: [
          const IconBadge(
            icon: Icons.credit_card_rounded,
            color: Color(0xFF0D9488),
            size: 38,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '校园卡',
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w700,
                    color: GlassColors.ink(context),
                  ),
                ),
                Text(
                  overview == null
                      ? '点击进入查看余额与本月流水'
                      : '本月消费 ¥${overview.monthSpend.toStringAsFixed(2)}（${overview.monthSpendCount}笔） · 充值 ¥${overview.monthRecharge.toStringAsFixed(2)}',
                  style: TextStyle(
                    fontSize: 12,
                    color: GlassColors.muted(context),
                  ),
                ),
              ],
            ),
          ),
          if (overview != null)
            Text(
              '¥${overview.balance.toStringAsFixed(2)}',
              style: const TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.w800,
                color: Color(0xFF0D9488),
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildQuickServicesGrid(BuildContext context) {
    final items = [
      ('成绩查询', Icons.bar_chart_rounded, const Color(0xFF6366F1), onOpenGrades),
      ('考试安排', Icons.event_note_rounded, const Color(0xFFEC4899), onOpenExams),
      ('空闲教室', Icons.meeting_room_outlined, const Color(0xFF3B82F6), onOpenClassrooms),
      ('通知公告', Icons.campaign_outlined, const Color(0xFFF97316), onOpenNotices),
      ('图书馆空位', Icons.menu_book_rounded, const Color(0xFF8B5CF6), onOpenLibrary),
      ('U净洗衣', Icons.local_laundry_service_rounded, const Color(0xFFEC4899), onOpenUjing),
      ('宿舍电费', Icons.bolt_rounded, const Color(0xFFF59E0B), onOpenElectricity),
      ('慧生活798', Icons.water_drop_outlined, const Color(0xFF0EA5E9), onOpenHuiLife),
      ('学习通', Icons.assignment_outlined, GlassColors.primary, onOpenChaoxing),
    ];
    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '全部校园服务',
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w700,
              color: GlassColors.ink(context),
            ),
          ),
          const SizedBox(height: 12),
          GridView.count(
            crossAxisCount: 3,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            mainAxisSpacing: 10,
            crossAxisSpacing: 10,
            childAspectRatio: 1.15,
            children: [
              for (final item in items)
                InkWell(
                  onTap: item.$4,
                  borderRadius: BorderRadius.circular(18),
                  child: Container(
                    decoration: BoxDecoration(
                      color: item.$3.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(18),
                    ),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        IconBadge(icon: item.$2, color: item.$3, size: 36),
                        const SizedBox(height: 6),
                        Text(
                          item.$1,
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            color: GlassColors.ink(context),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
            ],
          ),
        ],
      ),
    );
  }
}

class _TodayCourseTile extends StatelessWidget {
  final JwCourse course;

  const _TodayCourseTile({required this.course});

  @override
  Widget build(BuildContext context) {
    final start = ClassTimes.startTime(course.startPeriod) ?? '';
    final end = ClassTimes.endTime(course.endPeriod) ?? '';
    final color = GlassColors.coursePalette[
        course.name.hashCode.abs() % GlassColors.coursePalette.length];

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(16),
        border: Border(left: BorderSide(color: color, width: 4)),
      ),
      child: Row(
        children: [
          SizedBox(
            width: 58,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  start,
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: GlassColors.ink(context),
                  ),
                ),
                Text(
                  end,
                  style: TextStyle(
                    fontSize: 11,
                    color: GlassColors.muted(context),
                  ),
                ),
              ],
            ),
          ),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  course.name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                    color: GlassColors.ink(context),
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  [
                    '第${course.startPeriod}-${course.endPeriod}节',
                    if (course.room.isNotEmpty) course.room,
                    if (course.teacher.isNotEmpty) course.teacher,
                  ].join(' · '),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 12,
                    color: GlassColors.muted(context),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _TodayExamTile extends StatelessWidget {
  final JwExam exam;

  const _TodayExamTile({required this.exam});

  @override
  Widget build(BuildContext context) {
    const accent = Color(0xFFD93848);
    final timeStr = exam.startClock.isNotEmpty && exam.endClock.isNotEmpty
        ? '${exam.startClock}-${exam.endClock}'
        : (exam.startClock.isNotEmpty ? exam.startClock : '时间待定');
    final roomText = [
      exam.campus,
      exam.location,
      if (exam.seatNo.isNotEmpty) '座${exam.seatNo}',
    ].where((e) => e.isNotEmpty).join(' · ');

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
      decoration: BoxDecoration(
        color: accent.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: accent.withValues(alpha: 0.28)),
      ),
      child: Row(
        children: [
          Container(
            width: 3.5,
            height: 36,
            decoration: BoxDecoration(
              color: accent,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                      decoration: BoxDecoration(
                        color: accent,
                        borderRadius: BorderRadius.circular(5),
                      ),
                      child: const Text(
                        '考试',
                        style: TextStyle(
                          fontSize: 10,
                          color: Colors.white,
                          fontWeight: FontWeight.w800,
                        ),
                      ),
                    ),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        exam.courseName,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w700,
                          color: GlassColors.ink(context),
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 2),
                Text(
                  [timeStr, if (roomText.isNotEmpty) roomText].join(' · '),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 12,
                    color: GlassColors.muted(context),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _AlertBanner extends StatelessWidget {
  final Color color;
  final IconData icon;
  final String text;
  final String actionLabel;
  final VoidCallback onAction;

  const _AlertBanner({
    required this.color,
    required this.icon,
    required this.text,
    required this.actionLabel,
    required this.onAction,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: color.withValues(alpha: 0.35)),
      ),
      child: Row(
        children: [
          Icon(icon, color: color, size: 18),
          const SizedBox(width: 8),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                fontSize: 12.5,
                color: GlassColors.ink(context),
              ),
            ),
          ),
          TextButton(
            onPressed: onAction,
            child: Text(
              actionLabel,
              style: TextStyle(color: color, fontWeight: FontWeight.w700),
            ),
          ),
        ],
      ),
    );
  }
}
