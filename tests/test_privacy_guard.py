import contextlib
import importlib.util
import io
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('guard', Path(__file__).parents[1] / 'scripts' / 'privacy_guard.py')
guard = importlib.util.module_from_spec(spec)
spec.loader.exec_module(guard)

class GuardTests(unittest.TestCase):
    def test_git_identity_addresses_and_message_content(self):
        address = 'member' + '@private.example.net'
        trailer = 'Co-authored-by: Teammate <' + address + '>'
        secret = 'ghp_' + 'A' * 36
        with tempfile.TemporaryDirectory() as tmp, contextlib.chdir(tmp):
            guard.git('init')
            guard.git('config', 'user.name', 'TestUser')
            guard.git('config', 'user.email', address)
            guard.git('config', 'commit.gpgsign', 'false')
            def commit(message):
                guard.git('-c', 'core.hooksPath=/dev/null', 'commit', '--allow-empty', '-m', message)
                return guard.commit_findings('HEAD')
            self.assertEqual([], commit('test\n\n' + trailer + '\n' + trailer.lower()))
            self.assertIn('email-address', commit('Please contact ' + address))
            self.assertIn('email-address', commit('test\n\n' + trailer + '\n\nMore text\n\n' + trailer))
            self.assertIn('email-address', commit('test\n\nReviewed-by: Teammate <' + address + '>'))
            self.assertIn('email-address', commit('test\n\nCo-authored-by: ' + address + ' <' + address + '>'))
            self.assertIn('github-token', commit('test\n\nCo-authored-by: Member <' + secret + '@example.com>'))
            guard.git('config', 'user.email', secret + '@example.com')
            self.assertIn('github-token', commit('test'))
            Path('note.txt').write_text(trailer, encoding='utf-8')
            guard.git('add', 'note.txt')
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, guard.scan(staged=True))

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

    def test_reviewed_asset_is_exact_path_only(self):
        self.assertTrue(guard.REVIEWED_ASSETS)
        for path in guard.REVIEWED_ASSETS:
            self.assertEqual([], guard.path_findings(path))
        self.assertTrue(guard.path_findings('docs/questionnaire/other.pdf'))

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
