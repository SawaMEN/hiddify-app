import 'package:material_ui/material_ui.dart';

/// Platform defaults with system accessibility preferences taking precedence.
class VisualEffects extends InheritedWidget {
  const VisualEffects({super.key, required this.blur, required this.motion, required super.child});

  final bool blur;
  final bool motion;

  static VisualEffects? maybeOf(BuildContext context) => context.dependOnInheritedWidgetOfExactType<VisualEffects>();
  static bool blurOf(BuildContext context) => maybeOf(context)?.blur ?? false;
  static Duration durationOf(BuildContext context) =>
      (maybeOf(context)?.motion ?? !MediaQuery.disableAnimationsOf(context))
      ? const Duration(milliseconds: 180)
      : Duration.zero;

  @override
  bool updateShouldNotify(VisualEffects oldWidget) => blur != oldWidget.blur || motion != oldWidget.motion;
}

class VisualEffectsHost extends StatelessWidget {
  const VisualEffectsHost({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final media = MediaQuery.of(context);
    final reduced = media.disableAnimations || media.accessibleNavigation;
    final mobile = switch (Theme.of(context).platform) {
      TargetPlatform.android || TargetPlatform.iOS => true,
      _ => false,
    };
    return VisualEffects(blur: !reduced && !mobile, motion: !reduced, child: child);
  }
}
