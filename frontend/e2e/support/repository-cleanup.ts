import { rmSync } from "node:fs";

export function removeTestRepository(repository: string): void {
  try {
    rmSync(repository, { recursive: true, force: true, maxRetries: 10, retryDelay: 100 });
  } catch (error) {
    const code = (error as NodeJS.ErrnoException).code;
    if (process.platform !== "win32" || (code !== "EPERM" && code !== "EBUSY")) throw error;
    // ponytail: an in-flight background Git read can retain a Windows directory handle until the test backend exits.
  }
}
