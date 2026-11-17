import io
import unittest
import numpy as np
from scipy.io import wavfile
from audio_baseline import mix_at_snr, read_audio, summarize, noise_subset

class BaselineTests(unittest.TestCase):
    def test_pcm_scaling_and_resampling(self):
        buf=io.BytesIO();wavfile.write(buf,32000,np.full(32000,16384,dtype=np.int16))
        x=read_audio(buf.getvalue())
        self.assertEqual(len(x),16000)
        self.assertAlmostEqual(float(np.mean(x[100:-100])),.5,places=3)

    def test_snr_and_common_gain(self):
        signal=np.array([.9,-.9,.9,-.9],dtype=np.float32)
        noise=np.array([.7,.7,-.7,-.7],dtype=np.float32)
        for snr in [20,10,0]:
            mixed,gain=mix_at_snr(signal,noise,snr)
            added=mixed*gain-signal
            measured=10*np.log10(np.mean(signal**2)/np.mean(added**2))
            self.assertAlmostEqual(float(measured),snr,places=4)
            self.assertLessEqual(np.max(np.abs(mixed)),1.)

    def test_zero_noise_rejected(self):
        with self.assertRaises(ValueError):mix_at_snr(np.ones(4),np.zeros(4),10)

    def test_missing_doorbell_positive_not_zero_recall(self):
        rows=[{'category':'door_wood_knock','scores':{'Knock':.6,'Alarm clock':.2,'Doorbell':.7}},
              {'category':'dog','scores':{'Knock':.7,'Alarm clock':.1,'Doorbell':.1}}]
        report=summarize(rows)['classes']
        self.assertEqual(report['Knock']['hits'],1)
        self.assertEqual(report['Knock']['label_negative_alerts'],1)
        self.assertIsNone(report['Doorbell']['clip_recall'])

    def test_subset_uses_fold_five_and_all_positives(self):
        rows=[{'filename':str(i),'fold':f,'category':c} for i,(f,c) in enumerate(
            [(1,'door_wood_knock'),(5,'door_wood_knock'),(5,'door_wood_knock'),(5,'dog'),(5,'dog')])]
        chosen=noise_subset(rows)
        self.assertEqual(len(chosen),3)
        self.assertTrue(all(r['fold']==5 for r in chosen))

if __name__=='__main__':unittest.main()
