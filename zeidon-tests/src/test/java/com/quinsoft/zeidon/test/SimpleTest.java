/**
 *
 */
package com.quinsoft.zeidon.test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import com.quinsoft.zeidon.AttributeInstance;
import com.quinsoft.zeidon.CursorResult;
import com.quinsoft.zeidon.EntityCursor;
import com.quinsoft.zeidon.EntityInstance;
import com.quinsoft.zeidon.ObjectEngine;
import com.quinsoft.zeidon.Pagination;
import com.quinsoft.zeidon.SelectSet;
import com.quinsoft.zeidon.Task;
import com.quinsoft.zeidon.View;
import com.quinsoft.zeidon.ZeidonException;
import com.quinsoft.zeidon.objectdefinition.EntityDef;
import com.quinsoft.zeidon.objectdefinition.LodDef;
import com.quinsoft.zeidon.standardoe.JavaObjectEngine;
import com.quinsoft.zeidon.utils.QualificationBuilder;

/**
 * @author DG
 *
 */
class SimpleTest
{
    static int createChildEntities( int entityCount, View view, EntityDef entityDef )
    {
        if ( !entityDef.isCreate() )
            return entityCount;

        view.cursor( entityDef.getName() ).createEntity();
        entityCount++;
        for ( EntityDef child : entityDef.getChildren() )
        {
            for ( int i = 0; i < 2; i++ )
            {
                entityCount = createChildEntities( entityCount, view, child );
            }
        }
        return entityCount;
    }

    public static String getStackTraceString( Throwable aThrowable )
    {
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter( result );
        aThrowable.printStackTrace( printWriter );
        return result.toString();
    }

    static void stressTest()
    {
        ObjectEngine oe = JavaObjectEngine.getInstance();

        Task zeidonSystem = oe.getSystemTask();
        View view = zeidonSystem.activateEmptyObjectInstance( "kzwdlgxo" );
        LodDef lodDef = view.getLodDef();
        EntityDef rootEntity = lodDef.getRoot();
        int entityCount = 0;
        for ( int i = 0; i < 2000; i++ )
        {
            if ( i % 100 == 0 )
                view.log().debug( "Creating %s %d", rootEntity, i );

            entityCount = createChildEntities( entityCount, view, rootEntity );
            view.cursor( rootEntity.getName() ).getAttribute( "Tag").setValue( i ) ;
        }

        view.log().debug( "Created %d entities", entityCount );
        zeidonSystem.log().debug( "Starting cursor test" );

        // Run the test without needing to convert the attribute value every time.
        String tag = "1500";
        for ( int i = 0; i < 1000; i++ )
        {
            for ( CursorResult rc = view.cursor( "Dlg" ).setFirst();
                  rc.isSet();
                  rc = view.cursor( "Dlg" ).setNext() )
            {
                if ( view.cursor( "Dlg" ).getAttribute( "Tag").compare( tag )  == 0 )
                    break;
            }
        }
        zeidonSystem.log().debug( "Done cursor test 1" );

        // Rerun the test using an int value that must be converted to a string.
        for ( int i = 0; i < 1000; i++ )
        {
            for ( CursorResult rc = view.cursor( "Dlg" ).setFirst();
                  rc.isSet();
                  rc = view.cursor( "Dlg" ).setNext() )
            {
                if ( view.cursor( "Dlg" ).getAttribute( "Tag").compare( 1500 )  == 0 )
                    break;
            }
        }
        zeidonSystem.log().debug( "Done cursor test 2" );

        // Rerun the test but use a cursor object instead of the view.
        EntityCursor cursor = view.cursor( "Dlg" );
        for ( int i = 0; i < 1000; i++ )
        {
            for ( CursorResult rc = cursor.setFirst();
                  rc.isSet();
                  rc = cursor.setNext() )
            {
                if ( cursor.getAttribute( "Tag").compare( 1500 )  == 0 )
                    break;
            }
        }
        zeidonSystem.log().debug( "Done cursor test 3" );

        // One last time without the attribute conversion.
        for ( int i = 0; i < 1000; i++ )
        {
            for ( CursorResult rc = cursor.setFirst();
                  rc.isSet();
                  rc = cursor.setNext() )
            {
                if ( cursor.getAttribute( "Tag").compare( tag )  == 0 )
                    break;
            }
        }
        zeidonSystem.log().debug( "Done cursor test 4" );

//        for ( EntityInstance dlg : view.getEntityListUnderParent( "Dlg" ) )
//        {
//            zeidonSystem.log().debug( "Dlg %s", dlg.getAttribute( "Tag" ).getString() );
//            view.cursor( "Wnd" ).deleteEntity();
//        }
//        zeidonSystem.log().debug( "Done cursor test" );
    }

    private static void test( Task zencas )
    {
        View v = zencas.activateOiFromFile( "mStudent", "testdata/ZENCAs/mstudent_ac.por" );
        v.cursor("Student").getAttribute( "GeneralNote").setValue( v.cursor(  "Student" ).getAttribute(  "StudentLifeClearedDate" ).getValue() )  ;
        System.out.println( "done " + v.cursor( "Student" ).getAttribute( "GeneralNote" ).getString() );
    }

    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException
    {
        StringBuilder sb = new StringBuilder();
        System.out.println( sb.toString() );
        String properties = System.getProperties().toString();
        System.out.println( "CWD = " + System.getProperty("user.dir") );
        System.out.println( "CWD = " + System.getProperty("user.DIR") );
        System.out.println( "ZEIDON_HOME = " + System.getenv("ZEIDON_HOME") );

//        String fileDbUrl = "file:json:/tmp/filedb";
//        String fileDbUrl = "http://localhost:8080/test-restserver-1.0.6-SNAPSHOT/restserver";
        String fileDbUrl = "jdbc:sqlite:/home/dgc/zeidon/sqlite/zencasa.sqlite";
        ObjectEngine oe = JavaObjectEngine.getInstance();
//        oe.startBrowser();
        Task zencas = oe.createTask( "ZENCAs" );

//        Task cheetah = oe.createTask(  "Cheetah" );
//        View fPerson = new QualificationBuilder( cheetah )
//                                .setLodDef( "fPerson" )
//                                .setOiSourceUrl( "testsql:" )
//                                .loadFile( "testdata/Cheetah/KZDBHQUA_fPerson.json" )
//                                .activate();
//        fPerson.logObjectInstance();
//        if ( ! fPerson.isEmpty() )
//            return;

        View stud = new QualificationBuilder( zencas )
                            .setLodDef( "lStudDpt" )
                            .setOiSourceUrl( fileDbUrl )
//                            .addAttribQual( "MajorDepartment", "ID", "=", 3 )
                            .setPagination( new Pagination().setPageSize( 10 ) )
                            .addActivateOrdering( "Student", "CreationDate", true )
                            .activate();

        int count = 0;
        for ( EntityInstance ei : stud.cursor( "Student" ).eachEntity() )
        {
            stud.log().info( "Key = %s", ei.getAttribute( "ID" ) );
            stud.log().info( "Key = %s", stud.cursor( "Student" ).getAttribute( "ID" ) );
            stud.log().info( "%d -----------", count++ );
        }

        stud.cursor( "Student" ).getAttribute( "eMailAddress" ).setValue( "dgc@xyz.com" );
        stud.cursor( "Student" ).setPosition( 6 );
        String id = stud.cursor( "Student" ).getAttribute( "ID" ).getString();
//        stud.cursor(  "StudentMajorDegreeTrack" ).setPrevWithinOi();
        stud.serializeOi().asJson().withIncremental().toFile( "/tmp/stud.json" );
        String jsonFile = stud.serializeOi().withIncremental().toFile( "/tmp/stud2.json" );

        AttributeInstance attr = stud.cursor( "Student" ).getAttribute( "ID" );
        System.out.println( "US = " + attr.getString("en_US") );
        System.out.println( "FR = " + attr.getString("fr") );
        System.out.println( "DE = " + attr.getString("de") );

        View stud2 = zencas.deserializeOi()
                            .fromResource( jsonFile )
                            .setLodDef( "lStudDpt" )
                            .activateFirst();
//        stud2.logObjectInstance();

        stud2.setName( "ViewWithSelectSet" );
        SelectSet selectSet = stud2.getSelectSet( "TestSet" );
        selectSet.select( stud2.cursor( "Student" ) );
        stud2.cursor( "Student" ).setFirst();
        selectSet.select( stud2.cursor( "Student" ) );
        stud2.getSelectSet( "TestSet2" );
        stud2.getSelectSet( "TestSet3" );
        stud2.dropSelectSet( "TestSet2" );

        String xmlFile = stud.serializeOi().withIncremental().compressed().toTempDir( "stud2.xml" );
        stud2 = zencas.deserializeOi().fromResource( xmlFile ).activateFirst();

        if ( ! stud.cursor( "Student" ).getAttribute( "ID" ).getString().equals( stud2.cursor( "Student" ).getAttribute( "ID" ).getString() ) )
            throw new ZeidonException( "Mismatching IDs" );

        List<View> stud3 = zencas.deserializeOi()
                            .fromResource( "/tmp/stud.json" )
                            .activate();
        stud3.get( 0 ).logObjectInstance();

        String id2 = stud3.get( 0 ).cursor( "Student" ).getAttribute( "ID" ).getString();
        if ( ! id.equals( id2 ) )
            throw new ZeidonException( "Mismatching IDs" );

        //        stud.logObjectInstance();
/*
        CommitOptions options = new CommitOptions( zencas );
        options.setOiSourceUrl( fileDbUrl );
        stud.commit( options );

        stud = new QualificationBuilder( zencas )
                            .setLodDef( "lStudDpt" )
                            .addAttribQual( "eMailAddress", "kellysautter@comcast.net" )
                            .setOiSourceUrl( fileDbUrl )
                            .activate();
*/
    }

}