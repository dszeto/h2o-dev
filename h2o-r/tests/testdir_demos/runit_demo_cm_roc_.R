#----------------------------------------------------------------------
# Purpose:  Split Airlines dataset into train and validation sets.
#           Build model and predict on a test Set.
#           Print Confusion matrix and performance measures for test set
#----------------------------------------------------------------------

setwd(normalizePath(dirname(R.utils::commandArgs(asValues=TRUE)$"f")))
source('../h2o-runit.R')

options(echo=TRUE)

heading("BEGIN TEST")

# RStudio interactive mode
if (! exists("myIP")) {
  library(h2o)
  myIP = "localhost"
  myPort = 54321
}

conn <- h2o.init(ip=myIP, port=myPort, startH2O=FALSE)

#uploading data file to h2o
air <- h2o.importFile(conn, path=h2o:::.h2o.locate("smalldata/airlines/AirlinesTrain.csv.zip"))

#Constructing validation and train sets by sampling (20/80)
#creating a column as tall as airlines(nrow(air))
s <- h2o.runif(air)    # Useful when number of rows too large for R to handle
air.train <- air[s <= 0.8,]
air.valid <- air[s > 0.8,]

myX <- c("Origin", "Dest", "Distance", "UniqueCarrier", "fMonth", "fDayofMonth", "fDayOfWeek" )
myY <- "IsDepDelayed"

#gbm
air.gbm <- h2o.gbm(x = myX, y = myY, training_frame = air.train, validation_frame = air.valid,
                   loss = "bernoulli", ntrees = 100, max_depth = 3, learn_rate = 0.01)
print(air.gbm@model)
print(air.gbm@model$variableImportances[1:10,])

#glm
air.glm <- h2o.glm(x = myX, y = myY, training_frame = air.train, validation_frame = air.valid, family = "binomial")
print(air.glm@model)
print(air.glm@model$coefficients_magnitude[1:10,])

#uploading test file to h2o
air.test <- h2o.importFile(conn, path=h2o:::.h2o.locate("smalldata/airlines/AirlinesTest.csv.zip"))

#predicting & performance on test file
pred.gbm <- predict(air.gbm, air.test)
head(pred.gbm)
perf.gbm <- h2o.performance(air.gbm, air.test)
print(perf.gbm)

pred.glm <- predict(air.glm, air.test)
head(pred.glm)
perf.glm <- h2o.performance(air.glm, air.test)
print(perf.glm)

#Building confusion matrix for test set
CM.gbm <- h2o.confusionMatrices(perf.gbm, 0.5)
print(CM.gbm)
CM.glm <- h2o.confusionMatrices(perf.glm, 0.5)
print(CM.glm)

#Plot ROC for test set
h2o.precision(perf.gbm)
h2o.accuracy(perf.gbm)
h2o.auc(perf.gbm)
plot(perf.gbm,type="roc")

h2o.precision(perf.glm)
h2o.accuracy(perf.glm)
h2o.auc(perf.glm)
plot(perf.glm,type="roc")

PASS_BANNER()
