import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/features/wishlist/models/wishlist_item_response.dart';
import 'package:footverse/features/wishlist/providers/wishlist_provider.dart';
import 'package:footverse/features/wishlist/repositories/wishlist_repository.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'wishlist_provider_test.mocks.dart';

WishlistItemResponse _item({int id = 1, int productId = 10}) =>
    WishlistItemResponse(
      id: id,
      productId: productId,
      productName: 'Runner $productId',
      primaryImageUrl: null,
      basePrice: 100,
      available: true,
    );

const AppException _networkError = AppException.network(
  message: 'Unable to reach the server. Please check your connection.',
);

@GenerateNiceMocks([MockSpec<WishlistRepository>()])
void main() {
  late MockWishlistRepository repository;
  late WishlistProvider provider;

  setUp(() {
    repository = MockWishlistRepository();
    provider = WishlistProvider(repository);
  });

  group('load', () {
    test('moves loading → ready and exposes the server list', () async {
      final statuses = <WishlistStatus>[];
      provider.addListener(() => statuses.add(provider.status));
      when(
        repository.getWishlist(),
      ).thenAnswer((_) async => <WishlistItemResponse>[_item(productId: 10)]);

      await provider.load();

      expect(statuses, <WishlistStatus>[
        WishlistStatus.loading,
        WishlistStatus.ready,
      ]);
      expect(provider.items, hasLength(1));
      expect(provider.contains(10), isTrue);
      expect(provider.contains(99), isFalse);
      expect(provider.isEmpty, isFalse);
    });

    test('an empty wishlist loads to ready with no items', () async {
      when(
        repository.getWishlist(),
      ).thenAnswer((_) async => <WishlistItemResponse>[]);

      await provider.load();

      expect(provider.status, WishlistStatus.ready);
      expect(provider.isEmpty, isTrue);
    });

    test('a failed load moves to error and preserves the exception', () async {
      when(repository.getWishlist()).thenThrow(_networkError);

      await provider.load();

      expect(provider.status, WishlistStatus.error);
      expect(provider.error, same(_networkError));
    });
  });

  group('retry', () {
    test('re-runs the load and recovers from an earlier error', () async {
      when(repository.getWishlist()).thenThrow(_networkError);
      await provider.load();
      expect(provider.status, WishlistStatus.error);

      when(
        repository.getWishlist(),
      ).thenAnswer((_) async => <WishlistItemResponse>[_item(productId: 10)]);
      await provider.retry();

      expect(provider.status, WishlistStatus.ready);
      expect(provider.contains(10), isTrue);
    });
  });

  group('add', () {
    test('adds then reloads the server list so contains flips', () async {
      when(
        repository.getWishlist(),
      ).thenAnswer((_) async => <WishlistItemResponse>[]);
      await provider.load();
      expect(provider.contains(10), isFalse);

      when(
        repository.addProduct(any),
      ).thenAnswer((_) async => _item(productId: 10));
      when(
        repository.getWishlist(),
      ).thenAnswer((_) async => <WishlistItemResponse>[_item(productId: 10)]);

      await provider.add(10);

      final request = verify(repository.addProduct(captureAny)).captured.single;
      expect(request.productId, 10);
      // contains(10) flips only because the mutation reloaded the server list
      // (addProduct's return is not used) — proving reload-after-mutation.
      expect(provider.contains(10), isTrue);
      expect(provider.isMutating, isFalse);
    });

    test('a duplicate add resolves idempotently with no error', () async {
      when(
        repository.getWishlist(),
      ).thenAnswer((_) async => <WishlistItemResponse>[_item(productId: 10)]);
      await provider.load();
      when(
        repository.addProduct(any),
      ).thenAnswer((_) async => _item(productId: 10));

      await expectLater(provider.add(10), completes);

      expect(provider.contains(10), isTrue);
    });
  });

  group('remove', () {
    test('removes then reloads the server list', () async {
      when(
        repository.getWishlist(),
      ).thenAnswer((_) async => <WishlistItemResponse>[_item(productId: 10)]);
      await provider.load();
      expect(provider.contains(10), isTrue);

      when(repository.removeProduct(any)).thenAnswer((_) async {});
      when(
        repository.getWishlist(),
      ).thenAnswer((_) async => <WishlistItemResponse>[]);

      await provider.remove(10);

      verify(repository.removeProduct(10)).called(1);
      expect(provider.contains(10), isFalse);
      expect(provider.isEmpty, isTrue);
    });
  });

  group('single-flight', () {
    test('a second mutation while one is in flight is ignored', () async {
      final completer = Completer<WishlistItemResponse>();
      when(repository.addProduct(any)).thenAnswer((_) => completer.future);
      when(
        repository.getWishlist(),
      ).thenAnswer((_) async => <WishlistItemResponse>[_item(productId: 10)]);

      final first = provider.add(10);
      final second = provider.add(10);
      expect(provider.isMutating, isTrue);

      completer.complete(_item(productId: 10));
      await first;
      await second;

      verify(repository.addProduct(any)).called(1);
      expect(provider.isMutating, isFalse);
      expect(provider.contains(10), isTrue);
    });
  });

  group('reset', () {
    test('clears the wishlist state without calling the API', () async {
      when(
        repository.getWishlist(),
      ).thenAnswer((_) async => <WishlistItemResponse>[_item(productId: 10)]);
      await provider.load();
      expect(provider.contains(10), isTrue);

      provider.reset();

      expect(provider.items, isEmpty);
      expect(provider.contains(10), isFalse);
      verifyNever(repository.addProduct(any));
      verifyNever(repository.removeProduct(any));
    });

    test('discards a mutation still in flight when the session ends', () async {
      final completer = Completer<WishlistItemResponse>();
      when(repository.addProduct(any)).thenAnswer((_) => completer.future);
      when(
        repository.getWishlist(),
      ).thenAnswer((_) async => <WishlistItemResponse>[_item(productId: 10)]);

      final pending = provider.add(10);
      provider.reset();
      completer.complete(_item(productId: 10));
      await pending;

      // The stale add never repopulates the reset (signed-out) state.
      expect(provider.items, isEmpty);
      expect(provider.contains(10), isFalse);
    });
  });
}
