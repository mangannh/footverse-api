import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../providers/auth_provider.dart';

/// The signed-in area's minimal shell: it offers logout (sprint-6-plan item 08).
///
/// [AuthProvider.logout] revokes the refresh token and always clears the local
/// session; the resulting auth-state change makes the router redirect this
/// now-unauthenticated route to login. Full profile UI is out of scope.
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
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                const Text('You are signed in.'),
                const SizedBox(height: 24),
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
        ),
      ),
    );
  }
}
