"""Conservative project guard. No third-party dependencies; never prints matches."""
import argparse
import hashlib
from pathlib import PurePosixPath
import re
import subprocess
import sys

MAX_SIZE = 1024 * 1024
BLOCKED_DIRS = {'data', 'datasets', 'recordings', 'surveys', 'consents', 'private', 'secrets', 'logs'}
ALLOWED_SUFFIXES = {
    '.md', '.txt', '.py', '.kt', '.kts', '.java', '.xml', '.json', '.yaml', '.yml',
    '.toml', '.ini', '.cfg', '.properties', '.sh', '.ps1', '.bat', '.cmd', '.gradle',
    '.pro', '.js', '.ts', '.tsx', '.jsx', '.css', '.html', '.sql', '.proto',
}
ALLOWED_NAMES = {'.gitignore', '.gitattributes', 'codeowners', 'pre-commit', 'pre-push', 'license', '.env.example'}
# Public, reviewed binary assets (path -> max bytes). The blank questionnaire
# form holds no responses, names, or contact details; it is the only approved
# exception to the binary and size rules. New entries need an explicit review
# and a policy PR, never a wildcard or a directory prefix.
REVIEWED_ASSETS = {
    'docs/questionnaire/声境_调查问卷_A4竖版_4K_300DPI.pdf': 1024 * 1024,
}
PATTERNS = {
    'private-key': r'-----BEGIN (?:RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----',
    'github-token': r'\b(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{30,})\b',
    'api-key': r'\bsk-[A-Za-z0-9_-]{20,}\b',
    'cloud-access-key': r'\b(?:AKIA|ASIA)[A-Z0-9]{16}\b',
    'china-mobile': r'(?<![\w])(?:\+86[- ]?)?1[3-9]\d{9}(?!\w)',
    'china-id-shape': r'(?<!\w)[1-9]\d{5}(?:19|20)\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{3}[\dXx](?!\w)',
    'local-user-path': r'(?:[A-Za-z]:[\\/]Users[\\/][^\s/\\]+|/(?:Users|home)/[^\s/]+)',
    'assigned-secret': r'''(?i)(?:api[_-]?key|access[_-]?token|secret|password)\s*[=:]\s*["'](?!REPLACE_ME|YOUR_|EXAMPLE_|PLACEHOLDER)[A-Za-z0-9_+/=-]{12,}["']''',
}
EMAIL = re.compile(r'[A-Za-z0-9_.+-]+@([A-Za-z0-9.-]+\.[A-Za-z]{2,})')
# Owner explicitly authorized the default GitHub author email for merges.
# Digest avoids duplicating the address in source; this is not anonymization.
AUTHORIZED_AUTHOR_EMAIL_SHA256 = {'d3dcd7f778f2194bf585ce8be6fb4ec6856e180fa48f980c9e805c191f2c2e67'}
# The contributor consented to the author address on the squash commit for PR 12.
REVIEWED_AUTHOR_EMAIL = {
    'edf6c62039322aa09b3866267a59bc9b770f3723': '21bb4d087f90ad49f66cf0e07b86b84c55910e352e1162666f32c4dc90478af5',
}

def git(*args):
    return subprocess.check_output(['git', *args], stderr=subprocess.PIPE)

def content_findings(data, *, commit_author=False):
    try:
        text = data.decode('utf-8-sig')
    except UnicodeDecodeError:
        return ['non-utf8-or-binary']
    if '\0' in text:
        return ['binary-content']
    found = [name for name, regex in PATTERNS.items() if re.search(regex, text)]
    # GitHub creates squash commits with its public platform committer address.
    # Allow that exact address, never all mailboxes on github.com.
    if any(not (commit_author and hashlib.sha256(m.group(0).lower().encode()).hexdigest() in AUTHORIZED_AUTHOR_EMAIL_SHA256) and
           m.group(0).lower() != 'noreply@github.com' and
           m.group(1).lower() not in {'example.com', 'example.org', 'example.invalid', 'users.noreply.github.com'}
           for m in EMAIL.finditer(text)):
        found.append('email-address')
    return found

def path_findings(path):
    p = PurePosixPath(path.lower())
    found = []
    if any(part in BLOCKED_DIRS for part in p.parts):
        found.append('restricted-data-directory')
    if (p.name.startswith('.env') and p.name != '.env.example') or p.name == 'local.properties':
        found.append('local-secret-config')
    if path in REVIEWED_ASSETS:
        return found
    if p.suffix not in ALLOWED_SUFFIXES and p.name not in ALLOWED_NAMES:
        found.append('unapproved-file-type')
    return found

def entries(staged, ref):
    raw = git('ls-files', '--stage', '-z') if staged else git('ls-tree', '-r', '-z', ref)
    for record in raw.split(b'\0'):
        if not record:
            continue
        meta, name = record.split(b'\t', 1)
        fields = meta.decode().split()
        mode, oid = (fields[0], fields[1]) if staged else (fields[0], fields[2])
        yield mode, oid, name.decode('utf-8', errors='replace')

def scan(staged=False, history=False, ref='HEAD'):
    issues = set()
    refs = [ref]
    if history:
        refs = git('rev-list', ref).decode().splitlines()
    seen = set()
    for revision in refs:
        if not staged:
            # Check messages AND author/committer identity, without printing either.
            metadata = git('show', '-s', '--format=%B%n%cn <%ce>', revision)
            for reason in content_findings(metadata):
                issues.add(('commit-metadata', reason))
            oid, name, email = git('show', '-s', '--format=%H%x00%an%x00%ae', revision).rstrip(b'\n').split(b'\0')
            for reason in author_findings(oid.decode('ascii'), name, email):
                issues.add(('commit-metadata', reason))
        for mode, oid, path in entries(staged, revision):
            if (mode, oid, path) in seen:
                continue
            seen.add((mode, oid, path))
            for reason in path_findings(path) + content_findings(path.encode()):
                issues.add(('file', reason))
            if mode not in {'100644', '100755'}:
                issues.add(('file', 'symlink-or-submodule'))
                continue
            size = int(git('cat-file', '-s', oid))
            reviewed_limit = REVIEWED_ASSETS.get(path)
            if reviewed_limit is not None:
                if size > reviewed_limit:
                    issues.add(('file', 'reviewed-asset-over-limit'))
                continue
            if size > MAX_SIZE:
                issues.add(('file', 'over-1MiB'))
                continue
            for reason in content_findings(git('cat-file', 'blob', oid)):
                issues.add(('file', reason))
    for kind, reason in sorted(issues):
        print(f'BLOCKED: {kind}: {reason}')
    print('Privacy guard: FAILED (review locally; values and paths withheld).' if issues else 'Privacy guard: PASS (heuristic checks only).')
    return 1 if issues else 0

def author_findings(oid, name, email):
    if REVIEWED_AUTHOR_EMAIL.get(oid) == hashlib.sha256(email.lower()).hexdigest():
        # Keep every other check on both fields, including addresses in the name.
        return content_findings(name, commit_author=True) + [
            reason for reason in content_findings(email) if reason != 'email-address'
        ]
    return content_findings(name + b' <' + email + b'>', commit_author=True)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--staged', action='store_true')
    group.add_argument('--history', action='store_true')
    parser.add_argument('--ref', default='HEAD')
    args = parser.parse_args()
    try:
        sys.exit(scan(args.staged, args.history, args.ref))
    except (subprocess.CalledProcessError, ValueError):
        print('Privacy guard: unable to inspect Git objects; failing closed.', file=sys.stderr)
        sys.exit(2)
