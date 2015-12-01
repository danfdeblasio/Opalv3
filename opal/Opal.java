package opal;

import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.FileNotFoundException;

import opal.IO.Configuration;
import opal.IO.CostMatrix;
import opal.IO.OpalLogWriter;

import com.traviswheeler.libs.*;

import opal.makers.*;
import opal.polish.Polisher;
import opal.tree.Tree;
import opal.align.*;
import opal.exceptions.GenericOpalException;
import opal.IO.*;
import facet.FacetAlignment;
import facet.Facet;
import opal.realignment.realignmentDriver;

class runAlignment extends Thread{
	Configuration conf;
	Inputs in;
	AlignmentMaker am;
	int[][] alignmentInstance;
	double facetScore =-1;
	Configuration[] configList = null;
	
	
	
	runAlignment(Configuration c, Inputs i){
		conf = c;
		in = i;
	}
	runAlignment(Configuration c, Inputs i, Configuration[] cList){
		this(c,i);
		configList = cList;
	}
	
	public void run(){
		if(in.verbosity>1) System.err.println("Config : " + conf);
		
		try {		
			
			
			if (AlignmentMaker_SingleSequences.consistency) {
				int increase = AlignmentMaker_SingleSequences.consistency_subs_increase;
				conf.cost.increaseCosts(increase); // arbitrary number ... just trying to make w-w identities non-zero cost, and high enough to avoid rounding effects
				conf.cost.multiplyCosts(2);
				
				conf.increaseGapCosts(increase/2);
				conf.multiplyGapCosts(2);
			}
			
			
			
			if (Tree.treeType == Tree.TreeType.entered) {
				Tree.iterations = 1;
			}

			FacetAlignment fa = null;
			if (in.justDoSubOpt) {
				am = new AlignmentMaker_SuboptimalityTester();
				//am.initialize(in.fileA, in.structFileA);
				am.initialize(conf, in);
				alignmentInstance = am.buildAlignment();
				if(in.structFileA != null){
					fa = new FacetAlignment(conf.sc.convertIntsToSeqs(alignmentInstance),StructureFileReader.structure);
				}
			} else if (in.justDoConvert) {
				am = new AlignmentMaker_Converter();
				//am.initialize(in.fileA, in.structFileA);
				am.initialize(conf, in);
				alignmentInstance = am.buildAlignment();
				if(in.structFileA != null){
					fa = new FacetAlignment(conf.sc.convertIntsToSeqs(alignmentInstance),StructureFileReader.structure);
				}
			} else if (in.fileB != null) { // alignalign call
				am = new AlignmentMaker_TwoAlignments();
				//am.initialize(in.fileA, in.fileB);
				//Aligner.useStructure = false;
				am.initialize(conf, in);
				alignmentInstance = am.buildAlignment ();
				if(in.structFileA != null){
					fa = new FacetAlignment(((AlignmentMaker_TwoAlignments)am).result,StructureFileReader.structure);
				}
				
			} else if (in.justTree || Tree.justPWDists) { // 
				am = new AlignmentMaker_SingleSequences();
				//am.initialize(in.fileA, in.structFileA);
				am.initialize(conf, in);
				((AlignmentMaker_SingleSequences)am).buildTree(conf, in);
			} else { // multiple alignment call
				am = new AlignmentMaker_SingleSequences();
				//am.initialize(in.fileA, in.structFileA);
				am.initialize(conf, in);
				//final Timer queueRunner = new Timer(); 
				//ShutdownHandler sh = new ShutdownHandler(queueRunner, (AlignmentMaker_SingleSequences)am);
				//Runtime.getRuntime().addShutdownHook(sh);
				//Thread.setDefaultUncaughtExceptionHandler(sh);  // this should be available for all calls ... but this is the one I use a lot, so it's all I've implemented it for

				alignmentInstance = am.buildAlignment();
				if(in.structFileA != null){
					fa = new FacetAlignment(conf.sc.convertIntsToSeqs(alignmentInstance),StructureFileReader.structure);
					realignmentDriver realigner = new realignmentDriver(conf.sc.convertIntsToSeqs(alignmentInstance),StructureFileReader.structure, configList, conf, Facet.defaultValue(fa));
					realigner.simpleRealignment(5);
					alignmentInstance = realigner.newAlignment();
				}
			}
			
			if(fa != null){
				if(in.featureOutputFile!=null){
					String fname = in.featureOutputFile.replace("__CONFIG__", conf.toString());
					if(conf.repetition>=0) fname = fname.replace("__ITTERATION__", Integer.toString(conf.repetition));
					Facet.outputDefaultFeatures(fname, fa);
				}
				facetScore = Facet.defaultValue(fa);
			}
			//in.structFileA = "";
			//System.err.println("File: " + in.structFileA);
			
			
			//System.err.printf("facet value --  %.6f\n\n",facetScore);
		} catch (GenericOpalException e ) {
			System.exit(1);
		} /*catch (FileNotFoundException e){
			System.err.println("File Error:" + e.toString());
			System.exit(15);
		}*/
	}
	
	public void print(){
		if(in.configOutputFile != null){
			String fname = in.configOutputFile.replace("__CONFIG__", conf.toString());
			if(conf.repetition>=0) fname = fname.replace("__ITTERATION__", Integer.toString(conf.repetition));
			if(facetScore>=0) fname = fname.replace("__FACETSCORE__", "facetScore" + Double.toString(facetScore));
			am.printOutput(alignmentInstance, fname);
			
		}
		else am.printOutput(alignmentInstance, null);
		if(facetScore==-1){
			am = null;
		}
		if (in.verbosity>0 && facetScore>-1) {
			System.err.printf("facet score: %.6f\n",facetScore);
		}
	}
	
	public void printBest(){
		if (in.verbosity>-1) {
			System.err.printf("best facet value --  %.6f (%s)\n",facetScore, conf );
		}

		am.printOutput(alignmentInstance, in.bestOutputFile);
	}
	
}

public class Opal {


	public static void main(String[] argv) {

		OpalLogWriter.setLogger(new DefaultLogger());

		ArgumentHandler argHandler = new ArgumentHandler(argv);
				
		if (argHandler.getVerbosity() > 0) {
			OpalLogWriter.printVersion();
			OpalLogWriter.stdErrLogln("");
		}
		
		/*String fileA = argHandler.getFileA();
		String fileB = argHandler.getFileB();
		String structFileA = argHandler.getStructFileA();
		String structFileB = argHandler.getStructFileB();
		
		
		
		String costName = argHandler.getCostName(); 
		int gamma = argHandler.getGamma();
		int lambda = argHandler.getLambda();
		int gammaTerm = argHandler.getGammaTerm();
		int lambdaTerm = argHandler.getLambdaTerm();
			
		int verbosity = argHandler.getVerbosity();
		boolean toUpper = argHandler.isToUpper(); 
		boolean justDoConvert= argHandler.isJustDoConvert();
		boolean justDoSubOpt= argHandler.isJustDoSubOpt();
		boolean justTree= argHandler.isJustTree();*/
		
		
				
		Date start = new Date();
 
		

		Configuration[] config = argHandler.getConfigs();
		Inputs input = argHandler.getInputs();
		runAlignment[] thread = new runAlignment[config.length];
		
		int last_joined = -1;
		int max_threads = Runtime.getRuntime().availableProcessors();
		if(argHandler.getMaxThreads() > -1) max_threads = argHandler.getMaxThreads();
		if(input.verbosity>0){
			OpalLogWriter.stdErrLogln("The number of available threads is " + Runtime.getRuntime().availableProcessors() + ", Opal will use " + max_threads);
		}
		
		int maxIndex = 0;
		for(int i=0;i<config.length;i++){
			thread[i] = new runAlignment(config[i],input, config);
			//thread[i] = new printLine(config[i],i);
			thread[i].start();
			if(i-last_joined>=max_threads){
				last_joined++;
				try{
					thread[last_joined].join();
					thread[last_joined].print();
					if(thread[last_joined].facetScore > thread[maxIndex].facetScore){
						thread[maxIndex] = null;
						maxIndex = last_joined;
					}
					else if(last_joined != maxIndex) thread[last_joined] = null;
				}catch(InterruptedException e){
					OpalLogWriter.stdErrLogln("InterruptedException "+e.toString());
					throw new GenericOpalException("InterruptedException "+e.toString());
				}
			}
		}
		
		for(last_joined++;last_joined<config.length;last_joined++){
			try{
				thread[last_joined].join();
				thread[last_joined].print();
				if(thread[last_joined].facetScore > thread[maxIndex].facetScore){
					thread[maxIndex] = null;
					maxIndex = last_joined;
				}
				else if(last_joined != maxIndex) thread[last_joined] = null;
			}catch(InterruptedException e){
				OpalLogWriter.stdErrLogln("InterruptedException "+e.toString());
				throw new GenericOpalException("InterruptedException "+e.toString());
			}
		}
		
		if(config.length>1 && thread[maxIndex]!=null && thread[maxIndex].facetScore>=0) thread[maxIndex].printBest();
		
		if (input.verbosity>0) {
			Date now = new Date();
			long diff = now.getTime() - start.getTime();
			System.err.printf("Total time for job: %.1f seconds\n\n", ((float)diff/1000 + .05));
		}
		
		System.exit(0);
	}



		
}
	
