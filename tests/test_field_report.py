import json
import unittest
from pathlib import Path
from evaluation.field_report import summarize


class FieldTests(unittest.TestCase):
    def setUp(self):
        self.data=json.loads((Path(__file__).resolve().parents[1]/'evaluation/field-example.json').read_text())

    def test_three_denominators(self):
        report=summarize(self.data)
        self.assertEqual(2,report['phone']['detected'])
        self.assertEqual(1,report['phone']['false_alerts'])
        self.assertEqual(1,report['wrist_unknown'])
        self.assertEqual(1,report['wrist_seen_per_observed_request'])
        self.assertEqual(1/3,report['verified_hits_per_truth'])
        self.assertEqual(2.5,report['end_to_end_latency_p95_s'])

    def test_missing_observation_not_a_miss(self):
        self.data['phone']=self.data['phone'][1:]
        report=summarize(self.data)
        self.assertIsNone(report['wrist_seen_per_observed_request'])
        self.assertEqual(0,report['wrist_missed'])

    def test_seen_without_timestamp_counts_delivery_but_not_latency(self):
        del self.data['phone'][0]['wrist_s']
        report=summarize(self.data)
        self.assertEqual(1,report['verified_end_to_end_hits'])
        self.assertEqual(0,report['timed_end_to_end_hits'])
        self.assertIsNone(report['end_to_end_latency_p50_s'])

    def test_impossible_delivery_and_simulation_rejected(self):
        for key,value in [('delivery','failed'),('wrist_s',1),('simulated',True)]:
            with self.subTest(key=key):
                event=self.data['phone'][0]
                old=event[key]; event[key]=value
                with self.assertRaises(ValueError):summarize(self.data)
                event[key]=old
