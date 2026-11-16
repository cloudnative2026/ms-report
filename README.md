# ms-report

KPIs / analitica de Pedidos360. Java 21 / Spring Boot y Liquibase. En produccion usa el RDS MySQL compartido (base `report_db`,
creada al arrancar); el compose general usa PostgreSQL.

Consume `orders.events` (publicado por ms-orders) y mantiene una proyeccion por pedido
(`order_fact`). Tras 3 reintentos el mensaje va a `orders.events.ms-report.DLT`.
Los mensajes repetidos se ignoran por `eventId`.

## Ejecutar

```sh
docker compose up -d          # MySQL en 3306
./mvnw spring-boot:run        # Kafka en localhost:9092 (compose general)
```

Configuracion: DB_HOST, DB_PORT, DB_USERNAME, DB_PASSWORD, SPRING_KAFKA_BOOTSTRAP_SERVERS,
SERVER_PORT, ENTRA_ISSUER, ENTRA_AUDIENCE.

## API (solo lectura, rol Admin)

`from`/`to` son instantes ISO-8601 (`to` exclusivo); por defecto los ultimos 30 dias, maximo 366.

| Metodo | Ruta | Contenido |
| --- | --- | --- |
| GET | /api/v1/report/kpis | pedidos, ventas, ticket promedio, entregados, cancelados, lead time promedio |
| GET | /api/v1/report/sales | ventas y pedidos por dia (fecha de creacion) |
| GET | /api/v1/report/lead-time | horas creacion -> entrega por dia (fecha de entrega) |
| GET | /api/v1/report/top-products?limit=5 | unidades y ventas por producto |

Los pedidos cancelados no cuentan como ventas. Los dias usan la zona America/Santiago.
