import Combine
import FlowEntity

public struct KotlinFlowError: Error
{
    public let throwable: KotlinThrowable

    public init( _ throwable: KotlinThrowable )
    {
        self.throwable = throwable
    }
}

public struct KotlinFlowCastError: Error
{
    public let value: Any?
    public let expectedType: Any.Type

    public init( value: Any?, expectedType: Any.Type )
    {
        self.value = value
        self.expectedType = expectedType
    }
}

public extension SwiftObservable
{
    func asPublisher() -> AnyPublisher<Any?, Error>
    {
        Deferred {
            let subject = PassthroughSubject<Any?, Error>()
            var subscription: FlowSubscription?

            subscription = self.watchAny(
                onValue: {
                    subject.send( $0 )
                },
                onError: {
                    subject.send( completion: .failure( KotlinFlowError( $0 ) ) )
                }
            )

            return subject
                .handleEvents(
                    receiveCancel: {
                        subscription?.cancel()
                    }
                )
                .eraseToAnyPublisher()
        }
        .eraseToAnyPublisher()
    }

    func asPublisher<Value>( _ type: Value.Type ) -> AnyPublisher<Value, Error>
    {
        return asPublisher()
            .tryMap {
                guard let value = $0 as? Value else
                {
                    throw KotlinFlowCastError( value: $0, expectedType: type )
                }

                return value
            }
            .eraseToAnyPublisher()
    }

    func asOptionalPublisher<Value>( _ type: Value.Type ) -> AnyPublisher<Value?, Error>
    {
        return asPublisher()
            .map { $0 as? Value }
            .eraseToAnyPublisher()
    }

    func asPublisherNever() -> AnyPublisher<Any?, Never>
    {
        Deferred {
            let subject = PassthroughSubject<Any?, Never>()
            var subscription: FlowSubscription?

            subscription = self.watchAny(
                onValue: {
                    subject.send( $0 )
                },
                onError: { _ in
                    subject.send( completion: .finished )
                }
            )

            return subject
                .handleEvents(
                    receiveCancel: {
                        subscription?.cancel()
                    }
                )
                .eraseToAnyPublisher()
        }
        .eraseToAnyPublisher()
    }

    func asPublisherNever<Value>( _ type: Value.Type ) -> AnyPublisher<Value, Never>
    {
        return asPublisherNever()
            .compactMap { $0 as? Value }
            .eraseToAnyPublisher()
    }

    func asOptionalPublisherNever<Value>( _ type: Value.Type ) -> AnyPublisher<Value?, Never>
    {
        return asPublisherNever()
            .map { $0 as? Value }
            .eraseToAnyPublisher()
    }
}
