import 'package:material_ui/material_ui.dart';
import 'package:flutter_animate/flutter_animate.dart';
import 'package:hiddify/core/widget/skeleton_widget.dart';
import 'package:hiddify/core/theme/visual_effects.dart';

class ShimmerSkeleton extends StatelessWidget {
  const ShimmerSkeleton({
    this.width,
    this.height,
    this.widthFactor,
    this.heightFactor,
    this.color,
    this.duration = const Duration(seconds: 1),
    super.key,
  });

  final double? width;
  final double? height;
  final double? widthFactor;
  final double? heightFactor;
  final Color? color;
  final Duration duration;

  @override
  Widget build(BuildContext context) {
    final skeleton = Skeleton(width: width, height: height, widthFactor: widthFactor, heightFactor: heightFactor);
    if (!VisualEffects.blurOf(context)) return skeleton;
    return skeleton
        .animate(onPlay: (controller) => controller.loop())
        .shimmer(duration: duration, angle: 45, color: color ?? Theme.of(context).colorScheme.secondary);
  }
}
