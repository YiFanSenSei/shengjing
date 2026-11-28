import unittest
from evaluation.field_handoff import score


class HandoffTests(unittest.TestCase):
    def setUp(self):
        self.phone={'schema':'shengjing.phone.v1','round':'af123456','duration_s':60,
                    'simulated_excluded':2,'phone':[{'n':1,'at_s':5,'label':'knock','delivery':'requested'}]}
        self.notes={'schema':'shengjing.observer.v1','round':'af123456','complete':True,'observed_s':61,
                    'truth':[{'label':'knock','onset_s':4,'offset_s':6}],'wrist':[]}

    def test_unknown_wrist_not_imputed_from_phone(self):
        report=score(self.phone,self.notes)
        self.assertEqual(1,report['phone']['detected'])
        self.assertEqual(1,report['wrist_unknown'])
        self.assertIsNone(report['end_to_end_latency_p50_s'])

    def test_wrong_round_drafts_and_partial_observations_rejected(self):
        for patch in [{'round':'bb123456'},{'complete':False},{'unfinished':[['alarm',5]]},
                      {'observed_s':55},{'wrist':[{'n':9,'wrist':'seen'}]},
                      {'wrist':[{'n':1,'wrist':'unknown'},{'n':1,'wrist':'seen'}]}]:
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                score(self.phone,{**self.notes,**patch})

    def test_no_events_can_be_an_observed_negative(self):
        self.phone['phone']=[];self.notes['truth']=[]
        report=score(self.phone,self.notes)
        self.assertIsNone(report['phone']['recall'])
        self.assertEqual(0,report['phone']['false_alerts_per_hour'])
