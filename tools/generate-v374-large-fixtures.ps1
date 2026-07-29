param(
    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory
)

$ErrorActionPreference = "Stop"
$archiveRoot = [System.IO.Path]::GetFullPath($OutputDirectory)
[System.IO.Directory]::CreateDirectory($archiveRoot) | Out-Null
$fixturesRoot = [System.IO.Path]::Combine($archiveRoot, "large-fixtures")
[System.IO.Directory]::CreateDirectory($fixturesRoot) | Out-Null
$utf8 = [System.Text.UTF8Encoding]::new($false)
$weirdName = -join @(
    [char]0x4E0D, [char]0x77E5, [char]0x9053, [char]0x6709,
    [char]0x6CA1, [char]0x6709, [char]0x7528
)
$manifestItems = [System.Collections.Generic.List[object]]::new()

function Add-FixtureManifest {
    param(
        [string]$Name,
        [int]$LineCount,
        [string[]]$ExpectedFacts,
        [string]$Purpose
    )
    $target = [System.IO.Path]::Combine($fixturesRoot, $Name)
    $manifestItems.Add([ordered]@{
        relativePath = "large-fixtures/$Name"
        sha256 = (Get-FileHash -LiteralPath $target -Algorithm SHA256).Hash.ToLowerInvariant()
        bytes = (Get-Item -LiteralPath $target).Length
        lineCount = $LineCount
        expectedFactLocations = $ExpectedFacts
        purpose = $Purpose
    })
}

$writer = [System.IO.StreamWriter]::new(
    [System.IO.Path]::Combine($fixturesRoot, "large-code-80000.java"), $false, $utf8, 65536
)
try {
    for ($line = 1; $line -le 80000; $line++) {
        $value = switch ($line) {
            1 { "// OBSERVED HEAD: repository intake uses a bounded inventory"; break }
            40000 { "// OBSERVED MIDDLE: candidate writes cannot create ProjectFact"; break }
            79999 { "// VERIFIED TAIL: migration test and CI result validate the new boundary"; break }
            default { "final class GeneratedLine$line { int value() { return $line; } }" }
        }
        $writer.WriteLine($value)
    }
} finally {
    $writer.Dispose()
}
Add-FixtureManifest "large-code-80000.java" 80000 @("line:1", "line:40000", "line:79999") "80k code head, middle, tail, and symbol map"

$writer = [System.IO.StreamWriter]::new(
    [System.IO.Path]::Combine($fixturesRoot, "large-document-80000.md"), $false, $utf8, 65536
)
try {
    for ($line = 1; $line -le 80000; $line++) {
        $value = switch ($line) {
            1 { "# Current engineering contract"; break }
            20000 { "## Old declaration`nDECLARED: Agent Result can directly become a completed fact."; break }
            40000 { "## Middle decision`nOBSERVED: facts require source project and Evidence IDs."; break }
            60000 { "## Conflict`nCONFLICTED: an older final document still describes the direct-write path."; break }
            79999 { "## 2026 revision`nDEPRECATED: direct Agent fact writes are replaced by candidate submission and engineering validation."; break }
            default { "Background material line $line keeps the project context bounded and repeatable." }
        }
        $writer.WriteLine($value)
    }
} finally {
    $writer.Dispose()
}
Add-FixtureManifest "large-document-80000.md" 80004 @("line:1", "line:20000-20001", "line:40000-40001", "line:60000-60001", "line:80002-80003") "Long document sections, conflict, and tail revision"

$writer = [System.IO.StreamWriter]::new(
    [System.IO.Path]::Combine($fixturesRoot, $weirdName), $false, $utf8, 65536
)
try {
    for ($line = 1; $line -le 90000; $line++) {
        if ($line -eq 45000) {
            $writer.WriteLine("OBSERVED: Flyway migration V42 adds the project-scoped candidate table.")
        } else {
            $writer.WriteLine("Extensionless background material $line")
        }
    }
} finally {
    $writer.Dispose()
}
Add-FixtureManifest $weirdName 90000 @("line:45000") "Weird-name extensionless text with a middle fact"

$writer = [System.IO.StreamWriter]::new(
    [System.IO.Path]::Combine($fixturesRoot, "oversized-agent-result.json"), $false, $utf8, 65536
)
try {
    $writer.WriteLine('{')
    $writer.WriteLine('  "taskGoal": "large agent process evidence",')
    $writer.WriteLine('  "claim": "tests passed but no independent test or CI evidence is attached",')
    $writer.WriteLine('  "events": [')
    for ($line = 1; $line -le 60000; $line++) {
        $comma = if ($line -lt 60000) { "," } else { "" }
        $writer.WriteLine(('    {{"index":{0},"status":"claimed","evidence":"agent-only"}}{1}' -f $line, $comma))
    }
    $writer.WriteLine('  ]')
    $writer.WriteLine('}')
} finally {
    $writer.Dispose()
}
Add-FixtureManifest "oversized-agent-result.json" 60006 @("line:3") "Oversized Agent Result remains process evidence"

$writer = [System.IO.StreamWriter]::new(
    [System.IO.Path]::Combine($fixturesRoot, "large-structured.json"), $false, $utf8, 65536
)
try {
    $writer.WriteLine('{"services":[')
    for ($line = 1; $line -le 50000; $line++) {
        $marker = if ($line -eq 25000) { ',"observedBoundary":"project_id is mandatory"' } else { "" }
        $comma = if ($line -lt 50000) { "," } else { "" }
        $writer.WriteLine(('{{"id":{0},"name":"service-{0}"{1}}}{2}' -f $line, $marker, $comma))
    }
    $writer.WriteLine(']}')
} finally {
    $writer.Dispose()
}
Add-FixtureManifest "large-structured.json" 50002 @("line:25001") "Bounded Content Map for large structured JSON"

$writer = [System.IO.StreamWriter]::new(
    [System.IO.Path]::Combine($fixturesRoot, "large-structured.yaml"), $false, $utf8, 65536
)
try {
    $writer.WriteLine("modules:")
    for ($line = 1; $line -le 60000; $line++) {
        $writer.WriteLine("  - id: module-$line")
        if ($line -eq 30000) {
            $writer.WriteLine("    limitation: unknown ranges must remain disclosed")
        }
    }
} finally {
    $writer.Dispose()
}
Add-FixtureManifest "large-structured.yaml" 60002 @("line:30002") "Bounded Content Map for large structured YAML"

$manifest = [ordered]@{
    version = "projectflow-v3.7.4-large-fixtures-v1"
    rawArtifactsCommitted = $false
    fixtureCount = $manifestItems.Count
    fixtures = $manifestItems
}
$manifestPath = [System.IO.Path]::Combine($archiveRoot, "large-fixture-manifest.json")
[System.IO.File]::WriteAllText(
    $manifestPath,
    ($manifest | ConvertTo-Json -Depth 8),
    $utf8
)
$archiveHash = (Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
[System.IO.File]::WriteAllText(
    [System.IO.Path]::Combine($archiveRoot, "large-fixture-manifest.sha256"),
    "$archiveHash  large-fixture-manifest.json`n",
    $utf8
)

Write-Output "Generated $($manifestItems.Count) bounded V3.7.4 fixtures."
Write-Output "Manifest SHA-256: $archiveHash"
