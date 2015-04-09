package net.pizzaconnections.importer;

import java.io.IOException;
import java.net.URL;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.pizzaconnections.asm.Collector;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;

import com.orientechnologies.orient.core.intent.OIntentMassiveInsert;
import com.tinkerpop.blueprints.Compare;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;

public class OrientDBClassImporter {
	
	Pattern pattern = Pattern.compile("(.*)(\\/([a-zA-Z0-9_\\-\\.])*.jar\\!)(.*)");
	
	public static void main(String[] args) throws IOException {
		
		OrientDBClassImporter oi = new OrientDBClassImporter();
		Reflections reflections = new Reflections("com.orientechnologies", new SubTypesScanner(false)); 
		
		Set<Class<? extends Object>> subTypes = 
	               reflections.getSubTypesOf(Object.class);
		
		System.out.println("st "+subTypes);
		OrientGraphNoTx graph = new OrientGraphNoTx("plocal:D:\\orientdb-community-2.0.6\\databases\\OrientClassDiagram");
		graph.setRequireTransaction(false);
		graph.getRawGraph().declareIntent(new OIntentMassiveInsert());
		if(graph.getEdgeType("usedBy")==null){
			
			graph.createEdgeType("usedBy");
		}
		
		if(graph.getEdgeType("superclassOf")==null){
			
			graph.createEdgeType("superclassOf");
		}
		
		Iterator<Class<? extends Object>> i = subTypes.iterator();
		
		
		while(i.hasNext()){
			
			Class<? extends Object> c = i.next();
			oi.importClass(graph, c);
		}
		
	}

	private OrientVertex importClass(OrientGraphNoTx graph, Class<? extends Object> c) {
		// TODO Auto-generated method stub
		
		System.out.println("Importing Class : "+ c.getName());
		
		OrientVertex v=null;
		
		//Anonymous, inner class and array aren't imported
		if(c.isAnonymousClass() || c.getName().indexOf('$')!=-1 || c.getName().indexOf('[')!=-1)
			return v;
		
		
		String className = c.getSimpleName();
		String classPackage= c.getPackage().getName();
		String classType = c.isInterface()?"Interface":"Class";
		String classLibrary = "";
		
		//Get library name from class
		URL location = c.getResource('/'+c.getName().replace('.', '/')+".class");
		if(location!=null && location.getFile()!=null && location.getFile().indexOf(".jar")!=-1){
			classLibrary=this.extractLibraryName(location.getFile());
		}
		
		//Creating Vertex Type
		//Interface extends V
		//Class extends V
		if(c.isInterface()){
			if(graph.getVertexType("Interface")==null){
				
				graph.createVertexType("Interface");
				classType="Interface";
			}
		}
		else{
			if(graph.getVertexType("Class")==null){
				
				graph.createVertexType("Class");
				classType="Class";
			}
		}
		
		//Check if the class has already been imported
		Iterable<Vertex> queryVertex=(Iterable<Vertex>) graph.query().has("name", Compare.EQUAL, className).has("package", Compare.EQUAL, classPackage).vertices();
		
		Iterator<Vertex> iter=queryVertex.iterator();
		//If class has already been created returns the imported vertex
		if(queryVertex!=null && iter!=null &&  iter.hasNext()){
			return ((OrientVertex) queryVertex.iterator().next());
		}
			
		//Otherwise I create the vertex
		v = graph.addVertex("class:"+classType);
		v.setProperty("name", className);
		v.setProperty("package", classPackage);
		v.setProperties("library",classLibrary);
		
		//Checks if this class implements interfaces
		if(c.getInterfaces()!=null && c.getInterfaces().length>0){
			
			//Import the interfaces
			//Then for each interface create an edge : interface - superclassOf - v
			for(Class<?> i:c.getInterfaces()){
				if(i.getName()!=c.getName()){
					OrientVertex vi = importClass(graph, i);
					if(vi!=null){
						vi.addEdge("superclassOf", v);
						//graph.commit();
					}
				}
			}
		}
		
		//Check if this class extends another class
		if(c.getSuperclass()!=null){
			
			//Import the extended class (v1)
			//Then create an edge : v1 - superclassOf - v
			OrientVertex v1 = importClass(graph, c.getSuperclass());
			if(v1!=null){
				v1.addEdge("superclassOf", v);
				//graph.commit();
			}
			
		}
		
		//Check classes used by c 
		//only if package prefix is "com.orientechnologies"
		if(c.getPackage()!=null && c.getPackage().getName().indexOf("com.orientechnologies")!=-1){
			Set<Class<?>> usingClasses;
			try{
			 usingClasses = Collector.getClassesUsedBy(c.getName(), "");
			}catch(Exception e){
				usingClasses=null;
			}catch(UnsatisfiedLinkError e1){
				usingClasses=null;
			}catch(java.lang.NoClassDefFoundError e2){
				usingClasses=null;
			}
		
			if(usingClasses!=null && !c.isInterface() && !usingClasses.isEmpty() ){
				
				//Imported usedBy classes
				//For each class create an edge : vu - usedBy - v
				for(Class<?> c1:usingClasses){
					
					OrientVertex vu = importClass(graph, c1);
					
					if(vu!=null){
						vu.addEdge("usedBy", v);
					}
				}
				
			} 
		}
		
		
		return v;
	}
	
	private String extractLibraryName(String s){
		
		String extractedString="";
		
		
		Matcher m = pattern.matcher(s);
		
		while(m.find()){
			extractedString=m.group(2);
			extractedString=extractedString.substring(1,extractedString.length()-1);
		}
		
		
		return extractedString;

	}

}
