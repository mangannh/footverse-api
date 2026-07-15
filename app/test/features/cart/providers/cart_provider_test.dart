import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:footverse/core/error/app_exception.dart';
import 'package:footverse/features/cart/models/cart_item_response.dart';
import 'package:footverse/features/cart/models/cart_response.dart';
import 'package:footverse/features/cart/providers/cart_provider.dart';
import 'package:footverse/features/cart/repositories/cart_repository.dart';
import 'package:mockito/annotations.dart';
import 'package:mockito/mockito.dart';

import 'cart_provider_test.mocks.dart';

CartItemResponse _item({int id = 1, int quantity = 1, bool available = true}) =>
    CartItemResponse(
      id: id,
      productVariantId: 100 + id,
      productId: 200 + id,
      productName: 'Runner $id',
      productImageUrl: null,
      color: 'Black',
      size: '42',
      unitPrice: 50,
      quantity: quantity,
      lineTotal: (50 * quantity).toDouble(),
      available: available,
    );

CartResponse _cart({
  List<CartItemResponse>? items,
  double subtotal = 50,
  int itemCount = 1,
}) => CartResponse(
  items: items ?? <CartItemResponse>[_item()],
  subtotal: subtotal,
  itemCount: itemCount,
);

const AppException _networkError = AppException.network(
  message: 'Unable to reach the server. Please check your connection.',
);

const AppException _insufficientStock = AppException(
  message: 'Requested quantity exceeds available stock',
  statusCode: 400,
  errorCode: 'PRODUCT_VARIANT_INSUFFICIENT_STOCK',
);

const AppException _inactiveVariant = AppException(
  message: 'Product variant is not available for purchase',
  statusCode: 400,
  errorCode: 'PRODUCT_VARIANT_INACTIVE',
);

@GenerateNiceMocks([MockSpec<CartRepository>()])
void main() {
  late MockCartRepository repository;
  late CartProvider provider;

  setUp(() {
    repository = MockCartRepository();
    provider = CartProvider(repository);
  });

  group('load', () {
    test('moves loading → ready and exposes the returned cart', () async {
      final statuses = <CartStatus>[];
      provider.addListener(() => statuses.add(provider.status));
      when(
        repository.getCart(),
      ).thenAnswer((_) async => _cart(itemCount: 3, subtotal: 150));

      await provider.load();

      expect(statuses, <CartStatus>[CartStatus.loading, CartStatus.ready]);
      expect(provider.status, CartStatus.ready);
      expect(provider.itemCount, 3);
      expect(provider.cart?.subtotal, 150);
      expect(provider.error, isNull);
    });

    test('an empty cart loads to ready with no lines', () async {
      when(repository.getCart()).thenAnswer(
        (_) async =>
            _cart(items: <CartItemResponse>[], subtotal: 0, itemCount: 0),
      );

      await provider.load();

      expect(provider.status, CartStatus.ready);
      expect(provider.cart?.items, isEmpty);
      expect(provider.itemCount, 0);
    });

    test('a failed load moves to error and preserves the exception', () async {
      final statuses = <CartStatus>[];
      provider.addListener(() => statuses.add(provider.status));
      when(repository.getCart()).thenThrow(_networkError);

      await provider.load();

      expect(statuses, <CartStatus>[CartStatus.loading, CartStatus.error]);
      expect(provider.status, CartStatus.error);
      expect(provider.error, same(_networkError));
    });
  });

  group('retry', () {
    test('re-runs the load and recovers from an earlier error', () async {
      when(repository.getCart()).thenThrow(_networkError);
      await provider.load();
      expect(provider.status, CartStatus.error);

      when(repository.getCart()).thenAnswer((_) async => _cart(itemCount: 2));
      await provider.retry();

      expect(provider.status, CartStatus.ready);
      expect(provider.error, isNull);
      expect(provider.itemCount, 2);
    });
  });

  group('updateQuantity', () {
    test('replaces the whole state with the returned cart', () async {
      when(repository.getCart()).thenAnswer((_) async => _cart(itemCount: 1));
      await provider.load();
      when(
        repository.updateItem(any, any),
      ).thenAnswer((_) async => _cart(itemCount: 5, subtotal: 250));

      await provider.updateQuantity(1, 5);

      verify(repository.updateItem(1, any)).called(1);
      expect(provider.itemCount, 5);
      expect(provider.cart?.subtotal, 250);
      expect(provider.isMutating, isFalse);
    });

    test('rethrows PRODUCT_VARIANT_INSUFFICIENT_STOCK and leaves the cart '
        'unchanged', () async {
      when(repository.getCart()).thenAnswer((_) async => _cart(itemCount: 1));
      await provider.load();
      when(repository.updateItem(any, any)).thenThrow(_insufficientStock);

      await expectLater(
        provider.updateQuantity(1, 99),
        throwsA(
          isA<AppException>().having(
            (e) => e.errorCode,
            'errorCode',
            'PRODUCT_VARIANT_INSUFFICIENT_STOCK',
          ),
        ),
      );
      expect(provider.itemCount, 1);
      expect(provider.isMutating, isFalse);
    });
  });

  group('removeItem', () {
    test('replaces the whole state with the returned cart', () async {
      when(repository.getCart()).thenAnswer((_) async => _cart(itemCount: 2));
      await provider.load();
      when(repository.removeItem(any)).thenAnswer(
        (_) async =>
            _cart(items: <CartItemResponse>[], subtotal: 0, itemCount: 0),
      );

      await provider.removeItem(1);

      verify(repository.removeItem(1)).called(1);
      expect(provider.cart?.items, isEmpty);
      expect(provider.itemCount, 0);
    });
  });

  group('addItem', () {
    test(
      'adds one unit and replaces the state with the returned cart',
      () async {
        when(repository.getCart()).thenAnswer(
          (_) async =>
              _cart(items: <CartItemResponse>[], subtotal: 0, itemCount: 0),
        );
        await provider.load();
        when(
          repository.addItem(any),
        ).thenAnswer((_) async => _cart(itemCount: 1));

        await provider.addItem(101);

        final request = verify(repository.addItem(captureAny)).captured.single;
        expect(request.productVariantId, 101);
        expect(request.quantity, 1);
        expect(provider.itemCount, 1);
      },
    );

    test(
      'rethrows PRODUCT_VARIANT_INACTIVE and leaves the cart unchanged',
      () async {
        when(repository.getCart()).thenAnswer((_) async => _cart(itemCount: 1));
        await provider.load();
        when(repository.addItem(any)).thenThrow(_inactiveVariant);

        await expectLater(
          provider.addItem(500),
          throwsA(
            isA<AppException>().having(
              (e) => e.errorCode,
              'errorCode',
              'PRODUCT_VARIANT_INACTIVE',
            ),
          ),
        );
        expect(provider.itemCount, 1);
        expect(provider.isMutating, isFalse);
      },
    );
  });

  group('single-flight', () {
    test('a second mutation while one is in flight is ignored', () async {
      when(repository.getCart()).thenAnswer((_) async => _cart(itemCount: 1));
      await provider.load();

      final completer = Completer<CartResponse>();
      when(repository.updateItem(any, any)).thenAnswer((_) => completer.future);

      final first = provider.updateQuantity(1, 2);
      final second = provider.updateQuantity(1, 3);
      expect(provider.isMutating, isTrue);

      completer.complete(_cart(itemCount: 2));
      await first;
      await second;

      verify(repository.updateItem(any, any)).called(1);
      expect(provider.isMutating, isFalse);
      expect(provider.itemCount, 2);
    });
  });

  group('reset', () {
    test('clears the cart state without calling the API', () async {
      when(repository.getCart()).thenAnswer((_) async => _cart(itemCount: 3));
      await provider.load();
      expect(provider.itemCount, 3);

      provider.reset();

      expect(provider.cart, isNull);
      expect(provider.itemCount, 0);
      verifyNever(repository.removeItem(any));
    });

    test('discards a mutation still in flight when the session ends', () async {
      when(repository.getCart()).thenAnswer((_) async => _cart(itemCount: 1));
      await provider.load();

      final completer = Completer<CartResponse>();
      when(repository.updateItem(any, any)).thenAnswer((_) => completer.future);

      final pending = provider.updateQuantity(1, 9);
      provider.reset();
      completer.complete(_cart(itemCount: 9));
      await pending;

      // The stale response never repopulates the reset (signed-out) state.
      expect(provider.cart, isNull);
      expect(provider.itemCount, 0);
    });
  });
}
