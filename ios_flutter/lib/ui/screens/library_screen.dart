import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../core/campus_endpoints.dart';
import '../../data/models.dart';
import '../theme/glass_theme.dart';
import 'campus_web_page_screen.dart';

/// 图书馆空位查询（免登录）+ 官方 H5 预约/签到入口。
class LibraryScreen extends StatefulWidget {
  final CampusAppState appState;

  const LibraryScreen({super.key, required this.appState});

  @override
  State<LibraryScreen> createState() => _LibraryScreenState();
}

class _LibraryScreenState extends State<LibraryScreen> {
  late String _selectedDay;
  bool _onlyNow = false;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    _selectedDay = _fmtDate(now);
    WidgetsBinding.instance.addPostFrameCallback((_) => _refresh());
  }

  static String _fmtDate(DateTime dt) =>
      '${dt.year}-${dt.month.toString().padLeft(2, '0')}-${dt.day.toString().padLeft(2, '0')}';

  Future<void> _refresh() async {
    String? startTime;
    String? endTime;
    if (_onlyNow) {
      final now = DateTime.now();
      startTime =
          '${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}';
      endTime = '22:00';
    }
    await widget.appState.refreshLibrary(
      day: _selectedDay,
      startTime: startTime,
      endTime: endTime,
    );
  }

  @override
  Widget build(BuildContext context) {
    final app = widget.appState;
    final snap = app.librarySnapshot;
    final dates = (snap != null && snap.dates.isNotEmpty)
        ? snap.dates
        : [_fmtDate(DateTime.now())];

    return CampusBackdrop(
      child: Scaffold(
        appBar: AppBar(
          title: const Text('图书馆空位'),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => CampusWebPageScreen(
                      appState: app,
                      title: '图书馆官方预约',
                      url: CampusEndpoints.libraryH5Url,
                      desktopUserAgent: false,
                      helperText: '预约、签到请在官方 H5 完成，App 不保存图书馆密码。',
                    ),
                  ),
                );
              },
              child: const Text('官方预约'),
            ),
            IconButton(
              onPressed: app.loadingLibrary ? null : _refresh,
              icon: const Icon(Icons.refresh_rounded),
            ),
          ],
        ),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
          children: [
            GlassCard(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Wrap(
                    spacing: 8,
                    runSpacing: 6,
                    children: [
                      for (final d in dates)
                        ChoiceChip(
                          label: Text(d, style: const TextStyle(fontSize: 12)),
                          selected: _selectedDay == d,
                          onSelected: (_) {
                            setState(() => _selectedDay = d);
                            _refresh();
                          },
                        ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      ChoiceChip(
                        label: const Text('全天'),
                        selected: !_onlyNow,
                        onSelected: (_) {
                          setState(() => _onlyNow = false);
                          _refresh();
                        },
                      ),
                      const SizedBox(width: 8),
                      ChoiceChip(
                        label: const Text('现在起'),
                        selected: _onlyNow,
                        onSelected: (_) {
                          setState(() => _onlyNow = true);
                          _refresh();
                        },
                      ),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            if (app.loadingLibrary)
              const Padding(
                padding: EdgeInsets.all(24),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (app.libraryError != null)
              GlassCard(
                child: Text(
                  app.libraryError!,
                  style: const TextStyle(color: GlassColors.danger),
                ),
              )
            else if (snap != null)
              for (final campus in snap.campuses) ...[
                _CampusSeatCard(campus: campus),
                const SizedBox(height: 12),
              ],
          ],
        ),
      ),
    );
  }
}

class _CampusSeatCard extends StatelessWidget {
  final LibrarySeatArea campus;

  const _CampusSeatCard({required this.campus});

  @override
  Widget build(BuildContext context) {
    final ratio = campus.total > 0 ? campus.free / campus.total : 0.0;
    return GlassCard(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: Text(
                  campus.name,
                  style: TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w800,
                    color: GlassColors.ink(context),
                  ),
                ),
              ),
              Text(
                '空 ${campus.free} / ${campus.total}',
                style: const TextStyle(
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                  color: GlassColors.primary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: LinearProgressIndicator(
              value: ratio.clamp(0.0, 1.0),
              minHeight: 6,
              color: GlassColors.primary,
              backgroundColor: GlassColors.primary.withValues(alpha: 0.14),
            ),
          ),
          if (campus.children.isNotEmpty) ...[
            const SizedBox(height: 10),
            for (final floor in campus.children)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 4),
                child: Row(
                  children: [
                    Expanded(
                      child: Text(
                        floor.name,
                        style: TextStyle(
                          fontSize: 13.5,
                          color: GlassColors.ink(context),
                        ),
                      ),
                    ),
                    Text(
                      '${floor.free} / ${floor.total}',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.w600,
                        color: floor.free > 0
                            ? GlassColors.primary
                            : GlassColors.muted(context),
                      ),
                    ),
                  ],
                ),
              ),
          ],
        ],
      ),
    );
  }
}
