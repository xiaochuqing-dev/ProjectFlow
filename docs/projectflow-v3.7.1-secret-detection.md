# V3.7.1 Secret Detection Boundary

ProjectFlow uses a small outbound redaction layer before evidence samples, deep-read output, tool summaries or packed model context can cross the model boundary.

It combines:

- exact formats for private keys, GitHub tokens, AWS access keys, JWTs and Bearer values;
- credential-bearing database URL detection;
- credential-keyword assignments such as api key, access token, client secret, password and authorization;
- cautious high-entropy detection that excludes ordinary hex digests and obvious placeholders;
- sensitive-path denial for `.env` except `.env.example`, key/certificate containers and credential/secret directories.

This follows the mature format + keyword + entropy layering used by Gitleaks and detect-secrets. TruffleHog's verification approach was reviewed but not embedded because live credential verification would add network, provider and privacy side effects.

https://github.com/gitleaks/gitleaks

https://github.com/Yelp/detect-secrets

https://github.com/trufflesecurity/trufflehog

The layer is intentionally not marketed as a complete secret scanner. Repository-wide compliance scanning should continue to use a dedicated maintained tool. ProjectFlow's requirement is narrower: prevent known or suspicious credentials from entering model context, persisted diagnostics or Tool Result summaries. False-positive and false-negative cases remain testable and diagnosable.
