import 'package:flutter/material.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../app_state.dart';
import '../../core/campus_endpoints.dart';
import '../../network/electricity_client.dart';
import '../theme/glass_theme.dart';
import 'login_webview_screen.dart';
import 'runtime_config_screen.dart';

/// 宿舍电费页：电表余额 + 昨日用电 + 7天/30天柱状图（未结算日显示空白圆点）。
class ElectricityScreen extends StatefulWidget {
  final CampusAppState appState;

  const ElectricityScreen({super.key, required this.appState});

  @override
  State<ElectricityScreen> createState() => _ElectricityScreenState();
}

class _ElectricityScreenState extends State<ElectricityScreen> {
  int _daysWindow = 7;

  @override
  void initState() {
    super.initState();
    if (widget.appState.runtimeConfig.hasElectricity &&
        widget.appState.electricity == null) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        widget.appState.refreshElectricity();
      });
    }
  }

  Future<void> _openLogin() async {
    final pwd = widget.appState.runtimeConfig.electricityOperatorPwd;
    final ok = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => LoginWebViewScreen(
          appState: widget.appState,
          target: CampusEndpoints.electricityLogin(pwd),
        ),
      ),
    );
    if (ok == true && mounted) {
      await widget.appState.refreshElectricity();
    }
  }

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: widget.appState,
      builder: (context, _) {
        final app = widget.appState;
        final configured = app.runtimeConfig.hasElectricity;
        final snap = app.electricity;
        final usageMap = {
          for (final item in snap?.dailyUsage ?? const <ElectricityDayUsage>[])
            item.date: item.kwh,
        };
        final today = DateTime.now();
        final yesterdayKey = ElectricityClient.formatQueryDate(
          DateTime(today.year, today.month, today.day)
              .subtract(const Duration(days: 1)),
        );
        final yesterdayKwh = usageMap[yesterdayKey];

        final windowDates = List.generate(_daysWindow, (idx) {
          final d = DateTime(today.year, today.month, today.day)
              .subtract(Duration(days: _daysWindow - idx));
          return ElectricityClient.formatQueryDate(d);
        });
        final windowTotal = windowDates.fold<double>(
          0.0,
          (sum, d) => sum + (usageMap[d] ?? 0.0),
        );
        final maxVal = windowDates.fold<double>(
          1.0,
          (m, d) => (usageMap[d] ?? 0.0) > m ? usageMap[d]! : m,
        );

        return CampusBackdrop(
          child: Scaffold(
            appBar: AppBar(
              title: const Text('宿舍电费'),
              actions: [
                if (configured)
                  TextButton(onPressed: _openLogin, child: const Text('重登')),
                IconButton(
                  onPressed: app.loadingElectricity
                      ? null
                      : () => app.refreshElectricity(),
                  icon: app.loadingElectricity
                      ? const SizedBox(
                          width: 18,
                          height: 18,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Icon(Icons.refresh_rounded),
                ),
              ],
            ),
            body: ListView(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
              children: [
                if (!configured) ...[
                  GlassCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '尚未配置电费参数',
                          style: TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w800,
                            color: GlassColors.ink(context),
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          '公开版不内置第三方参数。你可以通过「高级配置」扫码/粘贴导入参数，或直接打开今日校园查询宿舍电费。',
                          style: TextStyle(
                            fontSize: 13,
                            height: 1.5,
                            color: GlassColors.muted(context),
                          ),
                        ),
                        const SizedBox(height: 12),
                        Wrap(
                          spacing: 10,
                          runSpacing: 8,
                          children: [
                            FilledButton.icon(
                              style: FilledButton.styleFrom(
                                backgroundColor: GlassColors.primary,
                              ),
                              onPressed: () {
                                Navigator.of(context).push(
                                  MaterialPageRoute(
                                    builder: (_) => RuntimeConfigScreen(
                                      appState: app,
                                    ),
                                  ),
                                );
                              },
                              icon: const Icon(Icons.tune_rounded, size: 18),
                              label: const Text('去高级配置导入'),
                            ),
                            OutlinedButton.icon(
                              onPressed: () => launchUrl(
                                Uri.parse('cpdaily://'),
                                mode: LaunchMode.externalApplication,
                              ),
                              icon: const Icon(Icons.open_in_new_rounded, size: 16),
                              label: const Text('打开今日校园'),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ] else ...[
                  if (app.electricityError != null) ...[
                    GlassCard(
                      child: Row(
                        children: [
                          const Icon(Icons.info_outline,
                              color: GlassColors.warning),
                          const SizedBox(width: 8),
                          Expanded(child: Text(app.electricityError!)),
                          TextButton(
                            onPressed: _openLogin,
                            child: const Text('去登录'),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(height: 12),
                  ],
                  Row(
                    children: [
                      Expanded(
                        child: GlassCard(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '电表余额',
                                style: TextStyle(
                                  fontSize: 12.5,
                                  color: GlassColors.muted(context),
                                ),
                              ),
                              const SizedBox(height: 6),
                              Text(
                                snap?.balance != null
                                    ? '¥${snap!.balance!.toStringAsFixed(2)}'
                                    : '--',
                                style: const TextStyle(
                                  fontSize: 26,
                                  fontWeight: FontWeight.w800,
                                  color: GlassColors.primary,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                      const SizedBox(width: 12),
                      Expanded(
                        child: GlassCard(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                '昨日用电',
                                style: TextStyle(
                                  fontSize: 12.5,
                                  color: GlassColors.muted(context),
                                ),
                              ),
                              const SizedBox(height: 6),
                              Text(
                                yesterdayKwh != null
                                    ? '${yesterdayKwh.toStringAsFixed(2)} 度'
                                    : '--',
                                style: const TextStyle(
                                  fontSize: 24,
                                  fontWeight: FontWeight.w800,
                                  color: Color(0xFFF59E0B),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                  if (snap != null && snap.houseAddress.isNotEmpty) ...[
                    const SizedBox(height: 8),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      child: Text(
                        '${snap.houseAddress} · 表号 ${snap.meterId}',
                        style: TextStyle(
                          fontSize: 12,
                          color: GlassColors.muted(context),
                        ),
                      ),
                    ),
                  ],
                  const SizedBox(height: 14),
                  GlassCard(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                '近 $_daysWindow 天用电（共 ${windowTotal.toStringAsFixed(1)} 度）',
                                style: TextStyle(
                                  fontSize: 15,
                                  fontWeight: FontWeight.w700,
                                  color: GlassColors.ink(context),
                                ),
                              ),
                            ),
                            ChoiceChip(
                              label: const Text('7天'),
                              selected: _daysWindow == 7,
                              visualDensity: VisualDensity.compact,
                              onSelected: (_) =>
                                  setState(() => _daysWindow = 7),
                            ),
                            const SizedBox(width: 6),
                            ChoiceChip(
                              label: const Text('30天'),
                              selected: _daysWindow == 30,
                              visualDensity: VisualDensity.compact,
                              onSelected: (_) =>
                                  setState(() => _daysWindow = 30),
                            ),
                          ],
                        ),
                        const SizedBox(height: 16),
                        SizedBox(
                          height: 120,
                          child: Row(
                            crossAxisAlignment: CrossAxisAlignment.end,
                            children: [
                              for (final d in windowDates)
                                Expanded(
                                  child: Padding(
                                    padding: EdgeInsets.symmetric(
                                      horizontal: _daysWindow == 7 ? 5 : 1.5,
                                    ),
                                    child: usageMap.containsKey(d)
                                        ? Tooltip(
                                            message:
                                                '$d: ${usageMap[d]!.toStringAsFixed(2)} 度',
                                            child: FractionallySizedBox(
                                              heightFactor:
                                                  (usageMap[d]! / maxVal)
                                                      .clamp(0.08, 1.0),
                                              child: Container(
                                                decoration: BoxDecoration(
                                                  color: const Color(0xFFF59E0B),
                                                  borderRadius:
                                                      BorderRadius.circular(6),
                                                ),
                                              ),
                                            ),
                                          )
                                        : Center(
                                            child: Container(
                                              width: 5,
                                              height: 5,
                                              decoration: BoxDecoration(
                                                shape: BoxShape.circle,
                                                border: Border.all(
                                                  color: GlassColors.muted(
                                                    context,
                                                  ).withValues(alpha: 0.45),
                                                ),
                                              ),
                                            ),
                                          ),
                                  ),
                                ),
                            ],
                          ),
                        ),
                        const SizedBox(height: 6),
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Text(
                              windowDates.first.substring(5),
                              style: TextStyle(
                                fontSize: 11,
                                color: GlassColors.muted(context),
                              ),
                            ),
                            Text(
                              '空白圆点表示接口未结算/无数据',
                              style: TextStyle(
                                fontSize: 10.5,
                                color: GlassColors.muted(context),
                              ),
                            ),
                            Text(
                              windowDates.last.substring(5),
                              style: TextStyle(
                                fontSize: 11,
                                color: GlassColors.muted(context),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 14),
                  if (snap != null && snap.dailyUsage.isNotEmpty) ...[
                    Text(
                      '历史日用量',
                      style: TextStyle(
                        fontSize: 14.5,
                        fontWeight: FontWeight.w700,
                        color: GlassColors.ink(context),
                      ),
                    ),
                    const SizedBox(height: 8),
                    GlassCard(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 14,
                        vertical: 6,
                      ),
                      child: Column(
                        children: [
                          for (var i = snap.dailyUsage.length - 1;
                              i >= 0;
                              i--) ...[
                            if (i < snap.dailyUsage.length - 1)
                              const Divider(height: 1),
                            ListTile(
                              dense: true,
                              contentPadding: EdgeInsets.zero,
                              title: Text(snap.dailyUsage[i].date),
                              trailing: Text(
                                '${snap.dailyUsage[i].kwh.toStringAsFixed(2)} 度',
                                style: const TextStyle(
                                  fontWeight: FontWeight.w700,
                                  color: Color(0xFFF59E0B),
                                ),
                              ),
                            ),
                          ],
                        ],
                      ),
                    ),
                  ],
                ],
              ],
            ),
          ),
        );
      },
    );
  }
}
