import contextlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('guard', Path(__file__).parents[1] / 'scripts' / 'privacy_guard.py')
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)

class GuardTests(unittest.TestCase):
    def test_author_exception_does_not_allow_content_or_other_authors(self):
        address = ('owner'+'@'+'private.example.net').encode()
        digest = guard.hashlib.sha256(address).hexdigest()
        with patch.object(guard, 'AUTHORIZED_AUTHOR_EMAIL_SHA256', {digest}):
            self.assertEqual([], guard.content_findings(address, commit_author=True))
            self.assertIn('email-address', guard.content_findings(address))
            self.assertIn('email-address', guard.content_findings(b'other'+address, commit_author=True))
            self.assertIn('github-token', guard.content_findings(address+b' ghp_'+b'A'*36, commit_author=True))

    def test_sensitive_shapes(self):
        for value in ['ghp_' + 'A' * 36, 'sk-' + 'b' * 32, '138' + '0' * 8, 'person' + '@' + 'real-domain.cn']:
            self.assertTrue(guard.content_findings(value.encode()))

    def test_safe_examples(self):
        self.assertEqual([], guard.content_findings(b'USER_001 REPLACE_ME user@example.com'))

    def test_github_platform_committer_only(self):
        self.assertEqual([], guard.content_findings(b'GitHub <noreply@github.com>'))
        for address in ['person'+'@github.com', 'noreply'+'@github.com.example.net']:
            self.assertIn('email-address', guard.content_findings(address.encode()))

    def test_restricted_files(self):
        for path in ['recordings/sample.wav', '.env', 'key.pem', 'survey.xlsx', 'image.png', 'local.properties']:
            self.assertTrue(guard.path_findings(path))

    def test_staged_content_and_deleted_history(self):
        old = os.getcwd()
        with tempfile.TemporaryDirectory() as tmp:
            try:
                os.chdir(tmp)
                def git(*args):
                    subprocess.run(['git', *args], check=True, capture_output=True)
                git('init')
                git('config', 'user.name', 'TestUser')
                git('config', 'user.email', 'test@example.com')
                git('config', 'commit.gpgsign', 'false')
                secret = 'ghp_' + 'A' * 36
                Path('sample.txt').write_text(secret)
                git('add', 'sample.txt')
                Path('sample.txt').write_text('clean working copy')
                with contextlib.redirect_stdout(io.StringIO()) as output:
                    self.assertEqual(1, guard.scan(staged=True))
                self.assertNotIn(secret, output.getvalue())
                git('-c', 'core.hooksPath=/dev/null', 'commit', '-m', 'fixture')
                git('add', 'sample.txt')
                git('-c', 'core.hooksPath=/dev/null', 'commit', '-m', 'clean')
                with contextlib.redirect_stdout(io.StringIO()):
                    self.assertEqual(0, guard.scan())
                    self.assertEqual(1, guard.scan(history=True))
            finally:
                os.chdir(old)

if __name__ == '__main__':
    unittest.main()
