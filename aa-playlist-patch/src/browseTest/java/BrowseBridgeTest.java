import java.util.*;
import java.util.concurrent.*;
import android.net.Uri;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.music.patches.RestoreAndroidAutoPlaylistsPatch;
import app.morphe.extension.music.patches.RestoreAndroidAutoPlaylistsPatch.*;
import com.google.common.util.concurrent.ListenableFuture;

public class BrowseBridgeTest {
 static void check(boolean value,String text) { if (!value) throw new AssertionError(text); }
 static class Request implements AndroidAutoPlaylistsRequest {
  final boolean root; final String id; int deliveries; List<MediaItem> items;
  Request(boolean root,String id) {this.root=root;this.id=id;}
  public boolean patch_isRoot(){return root;}
  public String patch_getRequestedMediaId(){return id;}
  public void patch_deliverAndroidAutoPlaylists(List<MediaItem> value){deliveries++;items=value;}
 }
 static class Response implements BrowseResponse {
  final Object section; int moreCalls;
  Response(Object section){this.section=section;}
  public Iterable<BrowseTab> patch_getTabs(){return List.of(()->()->List.of(section));}
  public GridRenderer patch_getMorePlaylists(){moreCalls++;throw new IllegalArgumentException("First page is not a continuation");}
  public OpenedPlaylistSongs patch_getMoreSongs(){moreCalls++;throw new IllegalArgumentException("First page is not a continuation");}
  public String patch_getPlayableMediaId(){throw new AssertionError("Do not resolve a playlist play action to browse it");}
 }
 static class Row implements PlaylistOrTrack {
  final String id; final boolean bad;
  Row(String id,boolean bad){this.id=id;this.bad=bad;}
  public String patch_getPlaylistBrowseId(){return id;}
  public String patch_getPlayableMediaId(){return "native-song-id";}
  public Uri patch_getArtworkUri(){return null;}
  public CharSequence patch_getTitle(){if(bad)throw new IllegalArgumentException();return "A song or playlist";}
  public CharSequence patch_getSubtitle(){return "";}
 }
 static class FutureResponse implements ListenableFuture<BrowseResponse> {
  BrowseResponse value; Runnable listener; Executor executor; boolean ready;
  FutureResponse(BrowseResponse value,boolean ready){this.value=value;this.ready=ready;}
  public void addListener(Runnable action,Executor executor){listener=action;this.executor=executor;if(ready)executor.execute(action);}
  void complete(){ready=true;executor.execute(listener);}
  public BrowseResponse get(){return value;}
  public BrowseResponse get(long time,TimeUnit unit){return value;}
  public boolean cancel(boolean interrupt){return false;} public boolean isCancelled(){return false;} public boolean isDone(){return ready;}
 }
 static class Phone implements PhoneBrowseRequests {
  FutureResponse response; int calls; String requested;
  public ListenableFuture<BrowseResponse> patch_requestBrowse(String id,Executor executor){calls++;requested=id;return response;}
  public ListenableFuture<BrowseResponse> patch_requestMorePlaylists(Object continuation,Executor executor){throw new AssertionError("Unexpected continuation");}
 }
 public static void main(String[] args) throws Exception {
  MusicHomeRendererTest.main(new String[0]);
  Phone phone=new Phone();
  RestoreAndroidAutoPlaylistsPatch.setPhoneBrowseRequests(phone);
  Request root=new Request(true,"root");
  check(RestoreAndroidAutoPlaylistsPatch.handleAndroidAutoPlaylists(root),"Root not handled");
  check(root.items.size()==3 && phone.calls==0,"Root must be three folders with no network or playback");
  for(MediaItem item:root.items)check(item.flags==1,"Root item is not browsable");
  String playlists=root.items.get(0).description.id;
  String history=root.items.get(1).description.id;
  GridRenderer grid=new GridRenderer(){
   public Iterable<?> patch_getRows(){return List.of(new Row("VL_one",true),new Row("VL_two",false),new Row("VL_two",false));}
   public Iterable<?> patch_getContinuationActions(){return List.of();}
  };
  Response library=new Response(grid);
  phone.response=new FutureResponse(library,true);
  Request list=new Request(false,playlists);
  RestoreAndroidAutoPlaylistsPatch.handleAndroidAutoPlaylists(list);
  check(library.moreCalls==0,"First page used continuation decoder");
  check(list.items.size()==1 && list.items.get(0).flags==1,"Bad row or duplicate broke playlist folders");
  check(phone.calls==1,"Playlist list fetched individual playlist play actions");
  Response songs=new Response(new OpenedPlaylistSongs(){
   public Iterable<PlaylistOrTrack> patch_getSongs(){return List.of(new Row(null,false));}
   public Iterable<?> patch_getContinuationActions(){return List.of();}
  });
  phone.response=new FutureResponse(songs,true);
  Request opened=new Request(false,list.items.get(0).description.id);
  RestoreAndroidAutoPlaylistsPatch.handleAndroidAutoPlaylists(opened);
  check("VL_two".equals(phone.requested),"Folder did not round-trip to its browse ID");
  check(opened.items.size()==1 && opened.items.get(0).flags==2,"Playlist songs missing");
  phone.response=new FutureResponse(songs,false);
  Request timeout=new Request(false,history);
  RestoreAndroidAutoPlaylistsPatch.handleAndroidAutoPlaylists(timeout);
  for(Runnable timer:List.copyOf(Utils.timers))timer.run();
  check(timeout.deliveries==1,"Timeout must complete once");
  phone.response.complete();
  check(timeout.deliveries==1,"Late response delivered twice");
  check(list.deliveries==1 && opened.deliveries==1,"Completed request was delivered twice by timer");
  check(!RestoreAndroidAutoPlaylistsPatch.handleAndroidAutoPlaylists(new Request(false,"unrelated")),"Unknown request intercepted");
  List<PlaylistOrTrack> many=new ArrayList<>();
  for(int i=0;i<81;i++){
   final String songId="song-"+i;
   many.add(new Row(null,false){public String patch_getPlayableMediaId(){return songId;}});
  }
  Response manySongs=new Response(new OpenedPlaylistSongs(){
   public Iterable<PlaylistOrTrack> patch_getSongs(){return many;}
   public Iterable<?> patch_getContinuationActions(){return List.of();}
  });
  phone.response=new FutureResponse(manySongs,true);
  Request first=new Request(false,history);
  RestoreAndroidAutoPlaylistsPatch.handleAndroidAutoPlaylists(first);
  check(first.items.size()==41 && first.items.get(40).flags==1,"Large list requires a More folder");
  Request second=new Request(false,first.items.get(40).description.id);
  RestoreAndroidAutoPlaylistsPatch.handleAndroidAutoPlaylists(second);
  check(second.items.size()==41 && second.items.get(0).description.id.equals("song-40"),"Second page offset lost");
  Request third=new Request(false,second.items.get(40).description.id);
  RestoreAndroidAutoPlaylistsPatch.handleAndroidAutoPlaylists(third);
  check(third.items.size()==1 && third.items.get(0).description.id.equals("song-80"),"Final page lost");
  // A short list can also exceed the byte budget when native command IDs are large.
  List<PlaylistOrTrack> largeIds=new ArrayList<>();
  for(int i=0;i<7;i++) {
   final String largeId="x".repeat(10000)+i;
   largeIds.add(new Row(null,false){public String patch_getPlayableMediaId(){return largeId;}});
  }
  phone.response=new FutureResponse(new Response(new OpenedPlaylistSongs(){
   public Iterable<PlaylistOrTrack> patch_getSongs(){return largeIds;}
   public Iterable<?> patch_getContinuationActions(){return List.of();}
  }),true);
  Request bytePage=new Request(false,history);
  RestoreAndroidAutoPlaylistsPatch.handleAndroidAutoPlaylists(bytePage);
  check(bytePage.items.size()==5 && bytePage.items.get(4).flags==1,"Byte budget must split before 40 items");
  Request byteNext=new Request(false,bytePage.items.get(4).description.id);
  RestoreAndroidAutoPlaylistsPatch.handleAndroidAutoPlaylists(byteNext);
  check(byteNext.items.size()==3 && byteNext.items.get(0).description.id.endsWith("4"),"Byte page offset lost");
  Object nextToken=new Object();
  OpenedPlaylistSongs firstSource=new OpenedPlaylistSongs(){
   public Iterable<PlaylistOrTrack> patch_getSongs(){return List.of(new Row(null,false));}
   public Iterable<?> patch_getContinuationActions(){return List.of(nextToken);}
  };
  OpenedPlaylistSongs secondSource=new OpenedPlaylistSongs(){
   public Iterable<PlaylistOrTrack> patch_getSongs(){return List.of(new Row(null,false){
    public String patch_getPlayableMediaId(){return "second-source-song";}
   });}
   public Iterable<?> patch_getContinuationActions(){return List.of(nextToken);}
  };
  int[] continuationCalls={0};
  Phone paginatedPhone=new Phone(){
   public ListenableFuture<BrowseResponse> patch_requestMorePlaylists(Object token,Executor executor){
    check(token==nextToken,"Wrong native continuation"); continuationCalls[0]++;
    return new FutureResponse(new Response(secondSource){
     public Iterable<BrowseTab> patch_getTabs(){return List.of();}
     public OpenedPlaylistSongs patch_getMoreSongs(){return secondSource;}
    },true);
   }
  };
  paginatedPhone.response=new FutureResponse(new Response(firstSource),true);
  RestoreAndroidAutoPlaylistsPatch.setPhoneBrowseRequests(paginatedPhone);
  Request sourcePages=new Request(false,history);
  RestoreAndroidAutoPlaylistsPatch.handleAndroidAutoPlaylists(sourcePages);
  check(sourcePages.items.size()==2 && sourcePages.deliveries==1 && continuationCalls[0]==1,
    "Source pagination lost songs or repeated a continuation token");
  check(!RestoreAndroidAutoPlaylistsPatch.handleAndroidAutoPlaylists(new Request(false,"%%%")),"Malformed ID intercepted");
  System.out.println("PASS: Binder-sized display pages and More folder continuation");
  System.out.println("PASS: root, first-page decoding, row isolation, deduplication, folder round-trip, song rows, timeout and late-response guard");
 }
}
