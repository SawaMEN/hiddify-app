import 'dart:ui';

import 'package:material_ui/material_ui.dart';
import 'package:hiddify/core/theme/visual_effects.dart';

/// Small, clipped glass regions only. Scrolling lists should keep [blur] off.
class GlassSurface extends StatelessWidget {
  const GlassSurface({super.key, required this.child, this.radius = 24, this.blur = false, this.accent});

  final Widget child;
  final double radius;
  final bool blur;
  final Color? accent;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final useBlur = blur && VisualEffects.blurOf(context);
    final tint = accent ?? scheme.primary;
    Widget surface = DecoratedBox(
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(radius),
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            Color.alphaBlend(
              tint.withValues(alpha: .08),
              scheme.surfaceContainerLow,
            ).withValues(alpha: useBlur ? .78 : 1),
            scheme.surfaceContainerLow.withValues(alpha: useBlur ? .92 : 1),
          ],
        ),
        border: Border.all(color: tint.withValues(alpha: .22)),
      ),
      child: Material(color: Colors.transparent, child: child),
    );
    if (useBlur) {
      surface = BackdropFilter(filter: ImageFilter.blur(sigmaX: 8, sigmaY: 8), child: surface);
    }
    return ClipRRect(borderRadius: BorderRadius.circular(radius), child: surface);
  }
}
