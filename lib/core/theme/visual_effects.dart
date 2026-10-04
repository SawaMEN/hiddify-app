import 'package:material_ui/material_ui.dart';
import 'package:hiddify/core/preferences/general_preferences.dart';
import 'package:hooks_riverpod/hooks_riverpod.dart';

/// A conservative default: mobile devices never pay for backdrop filters unless
/// the user opts in. System accessibility preferences always override quality.
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

class VisualEffectsHost extends ConsumerWidget {
  const VisualEffectsHost({super.key, required this.child});
  final Widget child;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final preference = ref.watch(Preferences.visualEffects);
    final media = MediaQuery.of(context);
    final reduced = preference == 'reduced' || media.disableAnimations || media.accessibleNavigation;
    final mobile = switch (Theme.of(context).platform) {
      TargetPlatform.android || TargetPlatform.iOS => true,
      _ => false,
    };
    return VisualEffects(blur: !reduced && (preference == 'quality' || !mobile), motion: !reduced, child: child);
  }
}
