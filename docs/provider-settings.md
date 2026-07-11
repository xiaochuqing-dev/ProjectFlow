# Provider Settings

The Provider Max Tokens value is a configuration ceiling, not the value sent by every task. Each structured task has its own policy limit, and diagnostics show the Provider ceiling, task limit, and final effective request value separately.

Structured tasks cap Temperature at 0.3. The default request timeout is 240 seconds. A normal request may receive one transport retry; an exhausted structured output receives one compact retry without another transport retry, keeping the total at three requests or fewer.

A connection test verifies basic URL, model, and Key availability only. It does not prove that a long structured analysis will fit its output budget or schema.

Keys remain encrypted in Provider storage, blank edits retain the existing key, and explicit clearing is required. Diagnostics never include keys, request headers, raw responses, or reasoning text.
