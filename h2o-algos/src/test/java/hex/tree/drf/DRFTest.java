package hex.tree.drf;


import org.junit.*;
import water.DKV;
import water.Key;
import water.Scope;
import water.TestUtil;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.Log;

import java.util.Arrays;

public class DRFTest extends TestUtil {
  @BeforeClass public static void stall() { stall_till_cloudsize(1); }

  abstract static class PrepData { abstract int prep(Frame fr); }

  static final String[] s(String...arr)  { return arr; }
  static final long[]   a(long ...arr)   { return arr; }
  static final long[][] a(long[] ...arr) { return arr; }

  @Test public void testClassIris1() throws Throwable {

    // iris ntree=1
    // the DRF should  use only subset of rows since it is using oob validation
    basicDRFTestOOBE_Classification(
            "./smalldata/iris/iris.csv", "iris.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                return fr.numCols() - 1;
              }
            },
            1,
            a(a(25, 0, 0),
                    a(0, 17, 1),
                    a(1, 2, 15)),
            s("Iris-setosa", "Iris-versicolor", "Iris-virginica"));

  }

  @Test public void testClassIris5() throws Throwable {
    // iris ntree=50
    basicDRFTestOOBE_Classification(
            "./smalldata/iris/iris.csv", "iris5.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                return fr.numCols() - 1;
              }
            },
            5,
            a(a(41, 0, 0),
                    a(0, 39, 3),
                    a(0, 4, 41)),
            s("Iris-setosa", "Iris-versicolor", "Iris-virginica"));
  }

  @Test public void testClassCars1() throws Throwable {
    // cars ntree=1
    basicDRFTestOOBE_Classification(
            "./smalldata/junit/cars.csv", "cars.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("name").remove();
                return fr.find("cylinders");
              }
            },
            1,
            a(a(0, 0, 0, 0, 0),
                    a(0, 62, 0, 7, 0),
                    a(0, 1, 0, 0, 0),
                    a(0, 0, 0, 31, 0),
                    a(0, 0, 0, 0, 40)),
            s("3", "4", "5", "6", "8"));
  }

  @Test public void testClassCars5() throws Throwable {
    basicDRFTestOOBE_Classification(
            "./smalldata/junit/cars.csv", "cars5.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("name").remove();
                return fr.find("cylinders");
              }
            },
            5,
            a(a(3, 0, 0, 0, 0),
                    a(0, 173, 2, 9, 0),
                    a(0, 1, 1, 0, 0),
                    a(0, 2, 2, 68, 2),
                    a(0, 0, 0, 2, 88)),
            s("3", "4", "5", "6", "8"));
  }

  @Test public void testConstantCols() throws Throwable {
    try {
      basicDRFTestOOBE_Classification(
              "./smalldata/poker/poker100", "poker.hex",
              new PrepData() {
                @Override
                int prep(Frame fr) {
                  for (int i = 0; i < 7; i++) {
                    fr.remove(3).remove();
                  }
                  return 3;
                }
              },
              1,
              null,
              null);
      Assert.fail();
    } catch( IllegalArgumentException iae ) {
    /*pass*/
    }
  }

  @Ignore @Test public void testBadData() throws Throwable {
    basicDRFTestOOBE_Classification(
            "./smalldata/junit/drf_infinities.csv", "infinitys.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                return fr.find("DateofBirth");
              }
            },
            1,
            a(a(6, 0),
                    a(9, 1)),
            s("0", "1"));
  }

  //@Test
  public void testCreditSample1() throws Throwable {
    basicDRFTestOOBE_Classification(
            "./smalldata/kaggle/creditsample-training.csv.gz", "credit.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("MonthlyIncome").remove();
                return fr.find("SeriousDlqin2yrs");
              }
            },
            1,
            a(a(46294, 202),
                    a(3187, 107)),
            s("0", "1"));

  }

  @Test public void testCreditProstate1() throws Throwable {
    basicDRFTestOOBE_Classification(
            "./smalldata/logreg/prostate.csv", "prostate.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("ID").remove();
                return fr.find("CAPSULE");
              }
            },
            1,
            a(a(0, 81),
                    a(0, 53)),
            s("0", "1"));

  }

  @Test public void testCreditProstateRegression1() throws Throwable {
    basicDRFTestOOBE_Regression(
            "./smalldata/logreg/prostate.csv", "prostateRegression.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("ID").remove();
                return fr.find("AGE");
              }
            },
            1,
            80.38636908313232
    );

  }

  @Test public void testCreditProstateRegression5() throws Throwable {
    basicDRFTestOOBE_Regression(
            "./smalldata/logreg/prostate.csv", "prostateRegression5.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("ID").remove();
                return fr.find("AGE");
              }
            },
            5,
            60.63686017358242
    );

  }

  @Test public void testCreditProstateRegression50() throws Throwable {
    basicDRFTestOOBE_Regression(
            "./smalldata/logreg/prostate.csv", "prostateRegression50.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                fr.remove("ID").remove();
                return fr.find("AGE");
              }
            },
            50,
            47.62149404858757
    );

  }

  @Ignore  //1-vs-5 node discrepancy
  @Test public void testAirlines() throws Throwable {
    basicDRFTestOOBE_Classification(
            "./smalldata/airlines/allyears2k_headers.zip", "airlines.hex",
            new PrepData() {
              @Override
              int prep(Frame fr) {
                for (String s : new String[]{
                        "DepTime", "ArrTime", "ActualElapsedTime",
                        "AirTime", "ArrDelay", "DepDelay", "Cancelled",
                        "CancellationCode", "CarrierDelay", "WeatherDelay",
                        "NASDelay", "SecurityDelay", "LateAircraftDelay", "IsArrDelayed"
                }) {
                  fr.remove(s).remove();
                }
                return fr.find("IsDepDelayed");
              }
            },
            7,
            a(a(4051, 15612), //for 5-node
              a(1397, 20322)),
//            a(a(4396, 15269), //for 1-node
//              a(1740, 19993)),
            s("NO", "YES"));
  }



  // Put response as the last vector in the frame and return possible frames to clean up later
  // Also fill DRF.
  static Vec unifyFrame(DRFModel.DRFParameters drf, Frame fr, PrepData prep, boolean classification) {
    int idx = prep.prep(fr);
    if( idx < 0 ) { idx = ~idx; }
    String rname = fr._names[idx];
    drf._response_column = fr.names()[idx];

    Vec resp = fr.vecs()[idx];
    Vec ret = null;
    if (classification) {
      ret = fr.remove(idx);
      fr.add(rname,resp.toEnum());
    } else {
      fr.remove(idx);
      fr.add(rname,resp);
    }
    return ret;
  }

  public void basicDRFTestOOBE_Classification(String fnametrain, String hexnametrain, PrepData prep, int ntree, long[][] expCM, String[] expRespDom) throws Throwable { basicDRF(fnametrain, hexnametrain, null, prep, ntree, expCM, expRespDom, -1, 10/*max_depth*/, 20/*nbins*/, true); }
  public void basicDRFTestOOBE_Regression(String fnametrain, String hexnametrain, PrepData prep, int ntree, double expMSE) throws Throwable { basicDRF(fnametrain, hexnametrain, null, prep, ntree, null, null, expMSE, 10/*max_depth*/, 20/*nbins*/, false); }

  public void basicDRF(String fnametrain, String hexnametrain, String fnametest, PrepData prep, int ntree, long[][] expCM, String[] expRespDom, double expMSE, int max_depth, int nbins, boolean classification) throws Throwable {
    Scope.enter();
    DRFModel.DRFParameters drf = new DRFModel.DRFParameters();
    Frame frTest = null, pred = null;
    Frame frTrain = null;
    Frame test = null, res = null;
    DRFModel model = null;
    try {
      frTrain = parse_test_file(fnametrain);
      Vec removeme = unifyFrame(drf, frTrain, prep, classification);
      if (removeme != null) Scope.track(removeme._key);
      DKV.put(frTrain._key, frTrain);
      // Configure DRF
      drf._train = frTrain._key;
      drf._response_column = ((Frame)DKV.getGet(drf._train)).lastVecName();
      drf._ntrees = ntree;
      drf._max_depth = max_depth;
      drf._min_rows = 1; // = nodesize
      drf._nbins = nbins;
      drf._mtries = -1;
      drf._sample_rate = 0.66667f;   // Simulated sampling with replacement
      drf._seed = (1L<<32)|2;
      drf._destination_key = Key.make("DRF_model_4_" + hexnametrain);

      // Invoke DRF and block till the end
      DRF job = null;
      try {
        job = new DRF(drf);
        // Get the model
        model = job.trainModel().get();
        Log.info(model._output);
      } finally {
        if (job != null) job.remove();
      }
      Assert.assertTrue(job._state == water.Job.JobState.DONE); //HEX-1817

      hex.ModelMetrics mm;
      if (fnametest != null) {
        frTest = parse_test_file(fnametest);
        pred = model.score(frTest);
        mm = hex.ModelMetrics.getFromDKV(model, frTest);
        // Check test set CM
      } else {
        mm = hex.ModelMetrics.getFromDKV(model, frTrain);
      }
      Assert.assertEquals("Number of trees differs!", ntree, model._output._ntrees);

      test = parse_test_file(fnametrain);
      res = model.score(test);

      if (classification) {
        Assert.assertTrue("Expected: " + Arrays.deepToString(expCM) + ", Got: " + Arrays.deepToString(mm.cm().confusion_matrix),
                Arrays.deepEquals(mm.cm().confusion_matrix, expCM));

        String[] cmDom = model._output._domains[model._output._domains.length - 1];
        Assert.assertArrayEquals("CM domain differs!", expRespDom, cmDom);
        Log.info("\nOOB Training CM:\n" + mm.cm().toASCII());
        Log.info("\nTraining CM:\n" + hex.ModelMetrics.getFromDKV(model, test).cm().toASCII());
      } else {
        Assert.assertTrue("Expected: " + expMSE + ", Got: " + mm.mse(), expMSE == mm.mse());
        Log.info("\nOOB Training MSE: " + mm.mse());
        Log.info("\nTraining MSE: " + hex.ModelMetrics.getFromDKV(model, test).mse());
      }

      hex.ModelMetrics.getFromDKV(model, test);

      // Build a POJO, validate same results
      Assert.assertTrue(model.testJavaScoring(test,res));

    } finally {
      if (frTrain!=null) frTrain.remove();
      if (frTest!=null) frTest.remove();
      if( model != null ) model.delete(); // Remove the model
      if( pred != null ) pred.delete();
      if( test != null ) test.delete();
      if( res != null ) res.delete();
      Scope.exit();
    }
  }
}
