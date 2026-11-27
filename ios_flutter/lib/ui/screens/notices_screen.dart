import 'package:flutter/material.dart';

import '../../app_state.dart';
import '../../core/campus_endpoints.dart';
import '../../data/models.dart';
import '../../network/jw_parser.dart';
import '../theme/glass_theme.dart';
import 'campus_web_page_screen.dart';

/// 教务学生通知公告：支持关键词检索、分页与正文阅读。
class NoticesScreen extends StatefulWidget {
  final CampusAppState appState;

  const NoticesScreen({super.key, required this.appState});

  @override
  State<NoticesScreen> createState() => _NoticesScreenState();
}

class _NoticesScreenState extends State<NoticesScreen> {
  final _searchCtrl = TextEditingController();
  List<JwNotice> _notices = const [];
  int _page = 0;
  bool _hasMore = true;
  bool _loading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _load(reset: true);
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  Future<void> _load({required bool reset}) async {
    if (_loading) return;
    setState(() {
      _loading = true;
      _error = null;
      if (reset) {
        _page = 0;
        _hasMore = true;
      }
    });
    try {
      final list = await widget.appState.jwAdapter.fetchNotices(
        keyword: _searchCtrl.text.trim(),
        page: _page,
      );
      if (!mounted) return;
      setState(() {
        _notices = reset ? list : [..._notices, ...list];
        _page++;
        _hasMore = list.length >= 10;
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

  void _openNotice(JwNotice notice) {
    final plain = JwParser.htmlToPlainText(notice.contentHtml);
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.72,
        maxChildSize: 0.92,
        builder: (_, controller) => Padding(
          padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
          child: ListView(
            controller: controller,
            children: [
              Text(
                notice.title,
                style: const TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w800,
                ),
              ),
              const SizedBox(height: 6),
              Text(
                [
                  if (notice.publisher.isNotEmpty) notice.publisher,
                  if (notice.publishedAt.isNotEmpty) notice.publishedAt,
                ].join(' · '),
                style: const TextStyle(fontSize: 12.5, color: Colors.grey),
              ),
              const Divider(height: 20),
              Text(
                plain.isNotEmpty ? plain : '该公告正文为空或仅含图片附件，请点下方打开官方页查看。',
                style: const TextStyle(fontSize: 14.5, height: 1.65),
              ),
              const SizedBox(height: 16),
              OutlinedButton.icon(
                onPressed: () {
                  Navigator.of(ctx).pop();
                  Navigator.of(context).push(
                    MaterialPageRoute(
                      builder: (_) => CampusWebPageScreen(
                        appState: widget.appState,
                        title: '公告官方页',
                        url:
                            '${CampusEndpoints.jwBaseUrl}/jwglxt/xtgl/xwglMobile_cxXwDetail.html?doType=app&xwbh=${Uri.encodeQueryComponent(notice.id)}',
                      ),
                    ),
                  );
                },
                icon: const Icon(Icons.open_in_new_rounded, size: 16),
                label: const Text('在官方页查看完整排版 / 图片'),
              ),
            ],
          ),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return CampusBackdrop(
      child: Scaffold(
        appBar: AppBar(title: const Text('通知公告')),
        body: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 28),
          children: [
            GlassCard(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _searchCtrl,
                      decoration: const InputDecoration(
                        hintText: '搜索公告标题关键词…',
                        border: InputBorder.none,
                      ),
                      onSubmitted: (_) => _load(reset: true),
                    ),
                  ),
                  IconButton(
                    onPressed: () => _load(reset: true),
                    icon: const Icon(Icons.search_rounded),
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
              ),
            for (final notice in _notices) ...[
              GlassCard(
                onTap: () => _openNotice(notice),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      notice.title,
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w700,
                        color: GlassColors.ink(context),
                      ),
                    ),
                    const SizedBox(height: 6),
                    Text(
                      [
                        if (notice.publisher.isNotEmpty) notice.publisher,
                        if (notice.publishedAt.isNotEmpty) notice.publishedAt,
                      ].join(' · '),
                      style: TextStyle(
                        fontSize: 12,
                        color: GlassColors.muted(context),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 10),
            ],
            if (_loading)
              const Padding(
                padding: EdgeInsets.all(16),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (_hasMore && _notices.isNotEmpty)
              Center(
                child: OutlinedButton(
                  onPressed: () => _load(reset: false),
                  child: const Text('加载更多'),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
