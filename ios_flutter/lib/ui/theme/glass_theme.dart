import 'dart:ui';

import 'package:flutter/material.dart';

/// 冻小梨液态玻璃配色与公用视觉组件（支持浅色 / 深色系统主题）。
class GlassColors {
  static const Color primary = Color(0xFF10B981);
  static const Color primaryLight = Color(0xFF34D399);
  static const Color inkLight = Color(0xFF1E293B);
  static const Color mutedLight = Color(0xFF64748B);
  static const Color inkDark = Color(0xFFF1F5F9);
  static const Color mutedDark = Color(0xFF94A3B8);
  static const Color blueGlow = Color(0xFF7DD3FC);
  static const Color purpleGlow = Color(0xFFC4B5FD);
  static const Color danger = Color(0xFFEF4444);
  static const Color warning = Color(0xFFF59E0B);

  static const List<Color> coursePalette = [
    Color(0xFF3B82F6),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
    Color(0xFFEC4899),
    Color(0xFF8B5CF6),
    Color(0xFF06B6D4),
    Color(0xFFF97316),
    Color(0xFF6366F1),
  ];

  static bool isDark(BuildContext context) =>
      Theme.of(context).brightness == Brightness.dark;

  static Color ink(BuildContext context) =>
      isDark(context) ? inkDark : inkLight;

  static Color muted(BuildContext context) =>
      isDark(context) ? mutedDark : mutedLight;
}

ThemeData buildCampusTheme(Brightness brightness) {
  final isDark = brightness == Brightness.dark;
  return ThemeData(
    useMaterial3: true,
    brightness: brightness,
    colorScheme: ColorScheme.fromSeed(
      seedColor: GlassColors.primary,
      brightness: brightness,
    ),
    scaffoldBackgroundColor: Colors.transparent,
    appBarTheme: AppBarTheme(
      backgroundColor: Colors.transparent,
      elevation: 0,
      scrolledUnderElevation: 0,
      foregroundColor: isDark ? GlassColors.inkDark : GlassColors.inkLight,
      titleTextStyle: TextStyle(
        fontSize: 19,
        fontWeight: FontWeight.w700,
        color: isDark ? GlassColors.inkDark : GlassColors.inkLight,
      ),
    ),
  );
}

/// 淡蓝 + 浅紫柔光渐变背景（深浅色独立配色）。
class CampusBackdrop extends StatelessWidget {
  final Widget child;

  const CampusBackdrop({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    final isDark = GlassColors.isDark(context);
    return Container(
      decoration: BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: isDark
              ? const [Color(0xFF0B1320), Color(0xFF131426), Color(0xFF0C1821)]
              : const [Color(0xFFEAF7FF), Color(0xFFF4EEFF), Color(0xFFE8F4FF)],
        ),
      ),
      child: Stack(
        children: [
          Positioned(
            top: -90,
            right: -70,
            child: _GlowCircle(
              size: 280,
              color: isDark
                  ? const Color(0x3310B981)
                  : const Color(0x5534D399),
            ),
          ),
          Positioned(
            bottom: 160,
            left: -110,
            child: _GlowCircle(
              size: 320,
              color: isDark
                  ? const Color(0x338B5CF6)
                  : const Color(0x55C4B5FD),
            ),
          ),
          Positioned(
            top: 300,
            right: -40,
            child: _GlowCircle(
              size: 220,
              color: isDark
                  ? const Color(0x2A38BDF8)
                  : const Color(0x447DD3FC),
            ),
          ),
          child,
        ],
      ),
    );
  }
}

class _GlowCircle extends StatelessWidget {
  final double size;
  final Color color;

  const _GlowCircle({required this.size, required this.color});

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: RadialGradient(
            colors: [color, color.withValues(alpha: 0)],
            stops: const [0, 0.72],
            radius: 0.72,
          ),
        ),
      ),
    );
  }
}

/// 液态玻璃卡片：顶部高光、色散描边、自适应深浅色透明度。
class GlassCard extends StatelessWidget {
  final Widget child;
  final EdgeInsetsGeometry padding;
  final double radius;
  final VoidCallback? onTap;

  const GlassCard({
    super.key,
    required this.child,
    this.padding = const EdgeInsets.all(16),
    this.radius = 24,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = GlassColors.isDark(context);
    final borderRadius = BorderRadius.circular(radius);

    final content = Container(
      decoration: BoxDecoration(
        borderRadius: borderRadius,
        border: Border.all(
          color: isDark
              ? Colors.white.withValues(alpha: 0.14)
              : Colors.white.withValues(alpha: 0.78),
          width: 1,
        ),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: isDark
              ? [
                  const Color(0xFF1E293B).withValues(alpha: 0.68),
                  const Color(0xFF0F172A).withValues(alpha: 0.54),
                ]
              : [
                  Colors.white.withValues(alpha: 0.78),
                  Colors.white.withValues(alpha: 0.56),
                ],
        ),
        boxShadow: [
          BoxShadow(
            color: (isDark ? Colors.black : GlassColors.blueGlow)
                .withValues(alpha: isDark ? 0.25 : 0.14),
            blurRadius: 24,
            spreadRadius: -8,
            offset: const Offset(0, 12),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: borderRadius,
        child: Material(
          color: Colors.transparent,
          child: InkWell(
            onTap: onTap,
            borderRadius: borderRadius,
            child: Padding(padding: padding, child: child),
          ),
        ),
      ),
    );
    return content;
  }
}

class NavTabItem {
  final String label;
  final IconData icon;
  final IconData activeIcon;

  const NavTabItem(this.label, this.icon, this.activeIcon);
}

/// 悬浮液态玻璃四栏底栏（中央内嵌绿色加号按钮，与 Android v0.5.2+ 保持一致）。
class LiquidGlassBottomBar extends StatelessWidget {
  final List<NavTabItem> items;
  final int currentIndex;
  final ValueChanged<int> onTabSelected;
  final VoidCallback onAddPressed;

  const LiquidGlassBottomBar({
    super.key,
    required this.items,
    required this.currentIndex,
    required this.onTabSelected,
    required this.onAddPressed,
  });

  static const double barHeight = 70;
  static const double contentBottomInset = 104;

  @override
  Widget build(BuildContext context) {
    final isDark = GlassColors.isDark(context);
    final radius = BorderRadius.circular(36);

    return SafeArea(
      top: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 10),
        child: Container(
          height: barHeight,
          decoration: BoxDecoration(
            borderRadius: radius,
            boxShadow: [
              BoxShadow(
                color: (isDark ? Colors.black : GlassColors.blueGlow)
                    .withValues(alpha: isDark ? 0.35 : 0.22),
                blurRadius: 28,
                spreadRadius: -8,
                offset: const Offset(0, 14),
              ),
              BoxShadow(
                color: (isDark ? Colors.black : GlassColors.purpleGlow)
                    .withValues(alpha: isDark ? 0.24 : 0.18),
                blurRadius: 22,
                spreadRadius: -8,
                offset: const Offset(0, 6),
              ),
            ],
          ),
          child: ClipRRect(
            borderRadius: radius,
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 22, sigmaY: 22),
              child: Container(
                decoration: BoxDecoration(
                  borderRadius: radius,
                  border: Border.all(
                    color: isDark
                        ? Colors.white.withValues(alpha: 0.16)
                        : Colors.white.withValues(alpha: 0.72),
                    width: 1,
                  ),
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: isDark
                        ? [
                            const Color(0xFF1E293B).withValues(alpha: 0.72),
                            const Color(0xFF0F172A).withValues(alpha: 0.86),
                          ]
                        : [
                            Colors.white.withValues(alpha: 0.56),
                            Colors.white.withValues(alpha: 0.84),
                          ],
                  ),
                ),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  child: Row(
                    children: [
                      for (var i = 0; i < items.length; i++) ...[
                        if (i == 2)
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 6),
                            child: _CenterAddButton(onTap: onAddPressed),
                          ),
                        Expanded(
                          child: _NavTabButton(
                            item: items[i],
                            selected: currentIndex == i,
                            onTap: () => onTabSelected(i),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _NavTabButton extends StatelessWidget {
  final NavTabItem item;
  final bool selected;
  final VoidCallback onTap;

  const _NavTabButton({
    required this.item,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final color = selected ? GlassColors.primary : GlassColors.muted(context);
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(22),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            selected ? item.activeIcon : item.icon,
            size: 22,
            color: color,
          ),
          const SizedBox(height: 2),
          Text(
            item.label,
            style: TextStyle(
              fontSize: 10.5,
              fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}

class _CenterAddButton extends StatelessWidget {
  final VoidCallback onTap;

  const _CenterAddButton({required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      shape: const CircleBorder(),
      clipBehavior: Clip.antiAlias,
      child: Ink(
        width: 52,
        height: 52,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          gradient: const LinearGradient(
            begin: Alignment.topLeft,
            end: Alignment.bottomRight,
            colors: [GlassColors.primaryLight, GlassColors.primary],
          ),
          boxShadow: [
            BoxShadow(
              color: GlassColors.primary.withValues(alpha: 0.35),
              blurRadius: 16,
              offset: const Offset(0, 6),
            ),
          ],
        ),
        child: InkWell(
          onTap: onTap,
          child: const Icon(Icons.add_rounded, color: Colors.white, size: 28),
        ),
      ),
    );
  }
}

/// 统一的图标徽章组件（纯 Material 图标，不显示单汉字）。
class IconBadge extends StatelessWidget {
  final IconData icon;
  final Color color;
  final double size;

  const IconBadge({
    super.key,
    required this.icon,
    required this.color,
    this.size = 40,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.14),
        borderRadius: BorderRadius.circular(size * 0.34),
      ),
      child: Icon(icon, color: color, size: size * 0.55),
    );
  }
}
