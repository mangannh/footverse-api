import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../../../core/router/app_routes.dart';
import '../providers/auth_provider.dart';

/// The signed-in area's minimal shell: it links to the customer's shopping
/// resources and offers logout (sprint-6-plan item 08; sprint-7-plan item 04).
///
/// The Addresses and Wishlist entries push their authenticated routes by name so
/// the system back button returns here (the screens themselves land in
/// sprint-7-plan items 05 and 08). [AuthProvider.logout] revokes the refresh
/// token and always clears the local session; the resulting auth-state change
/// makes the router redirect this now-unauthenticated route to login.
class AccountScreen extends StatefulWidget {
  const AccountScreen({super.key});

  @override
  State<AccountScreen> createState() => _AccountScreenState();
}

class _AccountScreenState extends State<AccountScreen> {
  bool _loggingOut = false;

  Future<void> _logout() async {
    final authProvider = context.read<AuthProvider>();
    setState(() => _loggingOut = true);
    await authProvider.logout();
    if (mounted) {
      setState(() => _loggingOut = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Account')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: <Widget>[
            ListTile(
              leading: const Icon(Icons.location_on_outlined),
              title: const Text('Addresses'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.pushNamed(AppRoute.addresses),
            ),
            ListTile(
              leading: const Icon(Icons.favorite_outline),
              title: const Text('Wishlist'),
              trailing: const Icon(Icons.chevron_right),
              onTap: () => context.pushNamed(AppRoute.wishlist),
            ),
            const Divider(),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: _loggingOut ? null : _logout,
              child: _loggingOut
                  ? const SizedBox(
                      height: 20,
                      width: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Text('Log out'),
            ),
          ],
        ),
      ),
    );
  }
}
