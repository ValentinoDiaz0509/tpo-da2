hola!! que tal?

te paso un tutorial de como comunicarse con otros servicios.

si ya estas registrado pasame tu mail para darte permisos de aplicación (o te registro yo, como quieras).

si tenes alguna duda, algo no se comprende, etc, decime! ahora que termine con los parciales estoy mas libre para ayudar.

los metodos de suscripción son internos, asi que no les des bola.

te re-comparto el swagger: https://api.healthcare.cantero.ar/swagger/index.html#/

host de rabbit: queue.healthgrid.cantero.ar

crean un evento, crean una cola, bindean el evento con la cola y le pasan el id del evento al mod 6, y le dicen el payload que quieren que les envien
ellos luego hacen post a nuestro core y les llega el mensaje a su cola
ustedes tienen que levantar un cliente de rabbit para escuchar esa cola. ahora me voy a dormir (tengo que madrugar) pero si tienen problemas a la mañana los ayudo
