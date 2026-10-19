# TCP protocol specification

Transport is a persistent TCP connection using UTF-8. Each command is terminated by `LF`; the server returns one response frame before reading the next command. A client may send multiple commands over one connection.

Commands:

* `PUT <key> <value>` stores the remainder of the line as the value and returns `OK STORED`.
* `GET <key>` returns `OK VALUE <value>` or `OK NOT_FOUND`.
* `DELETE <key>` returns `OK DELETED` or `OK NOT_FOUND`.
* `LIST` returns `OK LIST <count>`, `ITEM <base64url(key)> <base64url(value)>` lines, and `END LIST`.
* `PING` returns `OK PONG`.
* `STATS` returns a real metrics line, for example `OK STATS node=primary active=1 ...`.

Keys are non-empty and contain no whitespace. Values may contain spaces and UTF-8 characters but not CR/LF because the protocol is line framed. LIST uses URL-safe base64 without padding so values can contain spaces and punctuation without ambiguity.

Malformed requests return `ERROR BAD_REQUEST ...`; internal processing errors return `ERROR INTERNAL_ERROR`. A 30-second idle read timeout closes an abandoned client connection. The server uses a bounded worker pool, so overload is rejected and logged rather than allowed to grow without limit.
