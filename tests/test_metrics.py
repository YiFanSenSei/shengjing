import unittest
from evaluation.metrics import evaluate

class MetricsTests(unittest.TestCase):
    def test_misses_duplicates_and_false_alarms(self):
        truth=[{'label':'knock','onset_s':10,'offset_s':11},{'label':'bell','onset_s':20,'offset_s':21}]
        predictions=[{'label':'knock','at_s':10.5},{'label':'knock','at_s':11},{'label':'other','at_s':50}]
        result=evaluate(truth,predictions,3600)
        self.assertEqual(.5,result['recall']);self.assertEqual(2,result['false_alerts_per_hour'])
        self.assertEqual(1,result['duplicate_alerts']);self.assertEqual(.5,result['latency_p95_s'])
    def test_empty_reference(self):
        self.assertIsNone(evaluate([],[],60)['recall'])
    def test_future_prediction_cannot_match(self):
        result=evaluate([{'label':'knock','onset_s':10,'offset_s':11}],[{'label':'knock','at_s':9}],60)
        self.assertEqual(0,result['detected'])
    def test_bad_inputs(self):
        for duration in [0,-1,float('nan')]:
            with self.assertRaises(ValueError):evaluate([],[],duration)
