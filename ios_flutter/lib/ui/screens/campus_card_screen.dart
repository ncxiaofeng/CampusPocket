import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../theme/glass_theme.dart';

/// 校园卡页：ehall 一卡通余额、本月消费充值与交易流水明细。
class CampusCardScreen extends StatefulWidget {
  final CampusAppState appState;
  final VoidCallback onOpenOfficialPage;

  const CampusCardScreen({
    super.key,
    required this.appState,
    required this.onOpenOfficialPage,
  });

  @override
  State<CampusCardScreen> createState() => _CampusCardScreenState();
}

class _CampusCardScreenState extends State<CampusCardScreen> {
  @override
  void initState() {
    super.initState();
    if (widget.appState.cardOverview == null && widget.appState.hasCasMaster) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        widget.appState.refreshCard();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final app = widget.appState;
    final overview = app.cardOverview;

    return RefreshIndicator(
      onRefresh: () => app.refreshCard(),
      child: ListView(
        padding: const EdgeInsets.fromLTRB(
          16,
          12,
          16,
          LiquidGlassBottomBar.contentBottomInset,
        ),
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  '校园卡',
                  style: TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w800,
                    color: GlassColors.ink(context),
                  ),
                ),
              ),
              TextButton.icon(
                onPressed: widget.onOpenOfficialPage,
                icon: const Icon(Icons.open_in_new_rounded, size: 16),
                label: const Text('官方页'),
              ),
              IconButton(
                onPressed: app.loadingCard ? null : () => app.refreshCard(),
                icon: app.loadingCard
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.refresh_rounded),
              ),
            ],
          ),
          const SizedBox(height: 10),
          if (app.cardError != null) ...[
            GlassCard(
              child: Row(
                children: [
                  const Icon(Icons.info_outline, color: GlassColors.warning),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      app.cardError!,
                      style: TextStyle(
                        fontSize: 13,
                        color: GlassColors.ink(context),
                      ),
                    ),
                  ),
                  TextButton(
                    onPressed: widget.onOpenOfficialPage,
                    child: const Text('打开官方页'),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
          ],
          GlassCard(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '可用余额',
                  style: TextStyle(
                    fontSize: 13,
                    color: GlassColors.muted(context),
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  overview != null
                      ? '¥${overview.balance.toStringAsFixed(2)}'
                      : '--',
                  style: const TextStyle(
                    fontSize: 34,
                    fontWeight: FontWeight.w800,
                    color: Color(0xFF0D9488),
                  ),
                ),
                if (overview != null) ...[
                  const SizedBox(height: 6),
                  Text(
                    '状态：${overview.cardState.isEmpty ? '正常' : overview.cardState}'
                    '${overview.cardNumber.isNotEmpty ? ' · 卡号 ${overview.cardNumber}' : ''}',
                    style: TextStyle(
                      fontSize: 12,
                      color: GlassColors.muted(context),
                    ),
                  ),
                  const SizedBox(height: 14),
                  Row(
                    children: [
                      Expanded(
                        child: _StatPill(
                          label: '本月消费 (${overview.monthSpendCount}笔)',
                          value: '¥${overview.monthSpend.toStringAsFixed(2)}',
                          color: GlassColors.danger,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: _StatPill(
                          label: '本月充值 (${overview.monthRechargeCount}笔)',
                          value: '¥${overview.monthRecharge.toStringAsFixed(2)}',
                          color: GlassColors.primary,
                        ),
                      ),
                    ],
                  ),
                ] else ...[
                  const SizedBox(height: 10),
                  Text(
                    '首次使用请点击右上角「官方页」登录一次 ehall 一卡通，之后会自动静默刷新。',
                    style: TextStyle(
                      fontSize: 12.5,
                      color: GlassColors.muted(context),
                    ),
                  ),
                ],
              ],
            ),
          ),
          const SizedBox(height: 14),
          Text(
            '交易流水（${app.cardBills.length}/${app.cardBillsTotal}）',
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w700,
              color: GlassColors.ink(context),
            ),
          ),
          const SizedBox(height: 8),
          if (app.cardBills.isEmpty)
            GlassCard(
              child: Text(
                '暂无流水记录，请先登录并刷新。',
                style: TextStyle(color: GlassColors.muted(context)),
              ),
            )
          else
            GlassCard(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
              child: Column(
                children: [
                  for (var i = 0; i < app.cardBills.length; i++) ...[
                    if (i > 0) const Divider(height: 1),
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      title: Text(
                        app.cardBills[i].location.isNotEmpty
                            ? app.cardBills[i].location
                            : (app.cardBills[i].isRecharge ? '一卡通充值' : '校园消费'),
                        style: const TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      subtitle: Text(
                        app.cardBills[i].time,
                        style: TextStyle(
                          fontSize: 12,
                          color: GlassColors.muted(context),
                        ),
                      ),
                      trailing: Text(
                        '${app.cardBills[i].isRecharge ? '+' : '-'}¥${app.cardBills[i].amount.abs().toStringAsFixed(2)}',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                          color: app.cardBills[i].isRecharge
                              ? GlassColors.primary
                              : GlassColors.ink(context),
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          if (app.cardBills.length < app.cardBillsTotal) ...[
            const SizedBox(height: 10),
            Center(
              child: OutlinedButton(
                onPressed: app.loadingCard ? null : app.loadMoreCardBills,
                child: const Text('加载更多流水'),
              ),
            ),
          ],
        ],
      ),
    );
  }
}

class _StatPill extends StatelessWidget {
  final String label;
  final String value;
  final Color color;

  const _StatPill({
    required this.label,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.10),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: TextStyle(fontSize: 11.5, color: GlassColors.muted(context)),
          ),
          const SizedBox(height: 4),
          Text(
            value,
            style: TextStyle(
              fontSize: 16,
              fontWeight: FontWeight.w800,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}
