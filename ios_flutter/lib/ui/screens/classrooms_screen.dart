import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../core/campus_endpoints.dart';
import '../../data/models.dart';
import '../theme/glass_theme.dart';
import 'campus_web_page_screen.dart';

/// 空闲教室查询：支持校区、楼号、周次、星期、1-12 节多选，结果按教学楼分组。
class ClassroomsScreen extends StatefulWidget {
  final CampusAppState appState;

  const ClassroomsScreen({super.key, required this.appState});

  @override
  State<ClassroomsScreen> createState() => _ClassroomsScreenState();
}

class _ClassroomsScreenState extends State<ClassroomsScreen> {
  JwClassroomFilters? _filters;
  String _selectedBuildingId = '';
  late int _week;
  late int _weekday;
  final Set<int> _selectedPeriods = {1, 2};
  List<JwClassroom> _results = const [];
  bool _loading = false;
  String? _error;

  static const _weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

  @override
  void initState() {
    super.initState();
    _week = widget.appState.currentWeek;
    _weekday = DateTime.now().weekday;
    _loadFilters();
  }

  Future<void> _loadFilters() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final f = await widget.appState.jwAdapter.fetchClassroomFilters();
      if (!mounted) return;
      setState(() {
        _filters = f;
        _selectedBuildingId = '';
      });
      await _query();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = '$e';
        _loading = false;
      });
    }
  }

  Future<void> _onCampusChanged(String campusId) async {
    final filters = _filters;
    if (filters == null) return;
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final buildings = await widget.appState.jwAdapter.fetchClassroomBuildings(
        xnm: filters.xnm,
        xqm: filters.xqm,
        campusId: campusId,
      );
      final name = filters.campuses
              .where((c) => c.id == campusId)
              .map((c) => c.name)
              .firstOrNull ??
          filters.campusName;
      if (!mounted) return;
      setState(() {
        _filters = filters.copyWith(
          campusId: campusId,
          campusName: name,
          buildings: buildings,
        );
        _selectedBuildingId = '';
      });
      await _query();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = '$e';
        _loading = false;
      });
    }
  }

  Future<void> _query() async {
    final filters = _filters;
    if (filters == null) return;
    if (_selectedPeriods.isEmpty) {
      setState(() {
        _error = '请至少选择 1 个节次';
      });
      return;
    }
    var mask = 0;
    for (final p in _selectedPeriods) {
      mask |= 1 << (p - 1);
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final list = await widget.appState.jwAdapter.queryClassrooms(
        filters: filters,
        buildingId: _selectedBuildingId,
        week: _week,
        weekday: _weekday,
        periodMask: mask,
      );
      if (!mounted) return;
      setState(() {
        _results = list;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = '$e';
        _loading = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final grouped = <String, List<JwClassroom>>{};
    for (final room in _results) {
      final b = room.building.isEmpty ? '未分组教学楼' : room.building;
      grouped.putIfAbsent(b, () => []).add(room);
    }

    return CampusBackdrop(
      child: Scaffold(
        appBar: AppBar(
          title: const Text('空闲教室'),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => CampusWebPageScreen(
                      appState: widget.appState,
                      title: '空闲教室（官方页）',
                      url: CampusEndpoints.jwClassroomUrl,
                    ),
                  ),
                );
              },
              child: const Text('官方页'),
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
                  if (_filters != null) ...[
                    Row(
                      children: [
                        Expanded(
                          child: DropdownButtonFormField<String>(
                            initialValue: _filters!.campusId,
                            decoration: const InputDecoration(
                              labelText: '校区',
                              isDense: true,
                            ),
                            items: _filters!.campuses
                                .map(
                                  (c) => DropdownMenuItem(
                                    value: c.id,
                                    child: Text(c.name, overflow: TextOverflow.ellipsis),
                                  ),
                                )
                                .toList(),
                            onChanged: (v) {
                              if (v != null) _onCampusChanged(v);
                            },
                          ),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: DropdownButtonFormField<String>(
                            initialValue: _selectedBuildingId,
                            decoration: const InputDecoration(
                              labelText: '教学楼',
                              isDense: true,
                            ),
                            items: _filters!.buildings
                                .map(
                                  (b) => DropdownMenuItem(
                                    value: b.id,
                                    child: Text(b.name, overflow: TextOverflow.ellipsis),
                                  ),
                                )
                                .toList(),
                            onChanged: (v) {
                              if (v != null) {
                                setState(() => _selectedBuildingId = v);
                              }
                            },
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                  ],
                  Row(
                    children: [
                      Expanded(
                        child: DropdownButtonFormField<int>(
                          initialValue: _week,
                          decoration: const InputDecoration(
                            labelText: '周次',
                            isDense: true,
                          ),
                          items: [
                            for (var w = 1; w <= 20; w++)
                              DropdownMenuItem(value: w, child: Text('第 $w 周')),
                          ],
                          onChanged: (v) {
                            if (v != null) setState(() => _week = v);
                          },
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: DropdownButtonFormField<int>(
                          initialValue: _weekday,
                          decoration: const InputDecoration(
                            labelText: '星期',
                            isDense: true,
                          ),
                          items: [
                            for (var d = 1; d <= 7; d++)
                              DropdownMenuItem(
                                value: d,
                                child: Text(_weekdays[d - 1]),
                              ),
                          ],
                          onChanged: (v) {
                            if (v != null) setState(() => _weekday = v);
                          },
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Text(
                    '节次（可多选）',
                    style: TextStyle(
                      fontSize: 12,
                      color: GlassColors.muted(context),
                    ),
                  ),
                  const SizedBox(height: 6),
                  Wrap(
                    spacing: 6,
                    runSpacing: 6,
                    children: [
                      for (var p = 1; p <= 12; p++)
                        FilterChip(
                          label: Text('$p', style: const TextStyle(fontSize: 12)),
                          selected: _selectedPeriods.contains(p),
                          visualDensity: VisualDensity.compact,
                          onSelected: (selected) {
                            setState(() {
                              if (selected) {
                                _selectedPeriods.add(p);
                              } else {
                                _selectedPeriods.remove(p);
                              }
                            });
                          },
                        ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: _loading ? null : _query,
                      style: FilledButton.styleFrom(
                        backgroundColor: GlassColors.primary,
                      ),
                      icon: _loading
                          ? const SizedBox(
                              width: 16,
                              height: 16,
                              child: CircularProgressIndicator(
                                strokeWidth: 2,
                                color: Colors.white,
                              ),
                            )
                          : const Icon(Icons.search_rounded),
                      label: Text(_loading ? '查询中…' : '查询空闲教室'),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
            if (_error != null)
              GlassCard(
                child: Text(
                  _error!,
                  style: const TextStyle(color: GlassColors.danger),
                ),
              )
            else ...[
              Padding(
                padding: const EdgeInsets.only(bottom: 8, left: 4),
                child: Text(
                  '共找到 ${_results.length} 间空闲教室',
                  style: TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w700,
                    color: GlassColors.muted(context),
                  ),
                ),
              ),
              for (final entry in grouped.entries) ...[
                GlassCard(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        '${entry.key}（${entry.value.length} 间）',
                        style: TextStyle(
                          fontSize: 15,
                          fontWeight: FontWeight.w700,
                          color: GlassColors.ink(context),
                        ),
                      ),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 8,
                        children: [
                          for (final room in entry.value)
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 10,
                                vertical: 6,
                              ),
                              decoration: BoxDecoration(
                                color: GlassColors.primary.withValues(alpha: 0.10),
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: Text(
                                room.seats > 0
                                    ? '${room.name} · ${room.seats}座'
                                    : room.name,
                                style: TextStyle(
                                  fontSize: 12.5,
                                  fontWeight: FontWeight.w600,
                                  color: GlassColors.ink(context),
                                ),
                              ),
                            ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 10),
              ],
            ],
          ],
        ),
      ),
    );
  }
}
