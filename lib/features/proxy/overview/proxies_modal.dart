import 'package:material_ui/material_ui.dart';
import 'package:hiddify/features/proxy/overview/proxies_overview_page.dart';

Future<void> showProxiesModal(BuildContext context) => showModalBottomSheet<void>(
  context: context,
  useRootNavigator: true,
  isScrollControlled: true,
  showDragHandle: true,
  useSafeArea: true,
  constraints: const BoxConstraints(maxWidth: 900),
  builder: (context) => SizedBox(height: MediaQuery.sizeOf(context).height * .85, child: const ProxiesOverviewPage()),
);
