package com.xueyi.yang.kotlinandroid.fragment.home

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.PagerSnapHelper
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseQuickAdapter.OnItemChildClickListener
import com.xueyi.yang.kotlinandroid.R
import com.xueyi.yang.kotlinandroid.adapter.BannerAdapter
import com.xueyi.yang.kotlinandroid.adapter.HomeFragmentAdapter
import com.xueyi.yang.kotlinandroid.bean.BannerResponse
import com.xueyi.yang.kotlinandroid.bean.HomeDatas
import com.xueyi.yang.kotlinandroid.bean.HomeListResponse
import com.xueyi.yang.kotlinandroid.constant.Constant
import com.xueyi.yang.kotlinandroid.fragment.home.contract.HomeFragmentContract
import com.xueyi.yang.kotlinandroid.fragment.home.model.HomeFragmentModel
import com.xueyi.yang.kotlinandroid.fragment.home.presenter.HomeFragmentPresenter
import com.xueyi.yang.kotlinandroid.inflater
import com.xueyi.yang.kotlinandroid.module.homeContent.HomeContentActivity
import com.xueyi.yang.kotlinandroid.module.login.LoginActivity
import com.xueyi.yang.kotlinandroid.module.type.TypeContentActivity
import com.xueyi.yang.kotlinandroid.utils.SpUtils
import com.xueyi.yang.kotlinandroid.utils.ToastUtils
import com.xueyi.yang.kotlinandroid.view.HorizontalRecyclerView
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.launch

/**
 * Created by YangXueYi
 * Time : 2018/1/23.
 */
class HomeFragment : Fragment(),HomeFragmentContract.FragmentView {

    companion object {
        private const val BANNER_TIME = 3000L
    }
    /*mainView*/
    private var mainView : View? = null
    /*banner布局，使用水平recyclerview*/
    private lateinit var bannerRecyclerView :HorizontalRecyclerView
    /**
     * Banner switch job
     */
    private var bannerSwitchJob: Job? = null

    /*保存当前指数 */
    private var currentIndex = 0

    /*获取是否已经登录*/
    private var isLogin : Boolean by SpUtils(Constant.LOGIN_KEY,false)

    //homeadapter的数据
    private val lists = mutableListOf<HomeDatas>()
    //banner的数据
    private val bannerList = mutableListOf<BannerResponse.Data>()
    /*homeFragment的适配器*/
    private val homeAdapter : HomeFragmentAdapter by lazy {
        HomeFragmentAdapter(activity,lists)
    }
    /*banner的适配器*/
    private val bannerAdapter :BannerAdapter by lazy {
        BannerAdapter(activity,bannerList)
    }
    /*
     * 实现与viewpager一样的滑动功能
     */
    private val bannerPagerSnap: PagerSnapHelper by lazy {
        PagerSnapHelper()
    }
    /*bannerRecyclerView的方向:水平*/
    private val linearLayoutManager: LinearLayoutManager by lazy {
        LinearLayoutManager(activity, LinearLayoutManager.HORIZONTAL, false)
    }

    /*获取presenter*/
    private val homeFragmentPresenter : HomeFragmentPresenter by lazy {
         HomeFragmentPresenter(this,HomeFragmentModel())
    }

    @SuppressLint("InflateParams")
    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        /*
        * 如果HomeFragment的添加数据失败，那么就不显示banner轮播图
        * 所以添加了一个let函数
        * let:不为空的时候才执行lambda
        */
        mainView?:let {
            mainView = inflater?.inflate(R.layout.fragment_home,container,false)
            bannerRecyclerView = activity.inflater(R.layout.fragment_home_banner) as HorizontalRecyclerView
        }
        return mainView
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        //SwipeRefreshLayout的基本设置
        swipe_refresh.run {
            isRefreshing = true
            setOnRefreshListener(myOnRefreshListener)
        }
        //recycle_view基本设置
        recycle_view_home.run {
            layoutManager = LinearLayoutManager(activity)
            adapter = homeAdapter
        }
        //banner基本设置
        bannerRecyclerView.run {
            layoutManager = linearLayoutManager
            bannerPagerSnap.attachToRecyclerView(this)//实现与viewpager一样的滑动功能
            requestDisallowInterceptTouchEvent(true)//禁止拦截触摸事件
            setOnTouchListener(onTouchListener)//banner的触摸事件
            addOnScrollListener(onScrollListener)
        }
        //banneradapter基本设置
        bannerAdapter.run {
            bindToRecyclerView(bannerRecyclerView)//填充recycleview
            onItemClickListener = this@HomeFragment.bannerItemClickListener
        }
        //homeadapter的基本操作
        homeAdapter.run {
            bindToRecyclerView(recycle_view_home)//填充recycleview
            setOnLoadMoreListener(myRequestLoadMoreListener,recycle_view_home)//下拉加载更多的监听
            onItemClickListener = this@HomeFragment.onItemClickListener //条目的点击事件
            onItemChildClickListener = this@HomeFragment.onItemChildClickListener
            addHeaderView(bannerRecyclerView)
            setEmptyView(R.layout.fragment_empty)

        }
        //拿到banner内容
        homeFragmentPresenter.getBannerResult()
        //拿到recycle_view内容
        homeFragmentPresenter.getListResult()

    }

    /**
     * pause banner switch
     */
    override fun onPause() {
        super.onPause()
        cancelSwitchJob()
    }

    /**
     * resume banner switch
     */
    override fun onResume() {
        super.onResume()
        startSwitchJob()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden){
            cancelSwitchJob()
        }else
            startSwitchJob()
    }

    override fun getBannerSuccess(result: BannerResponse) {
        result.data.let {
            bannerAdapter.replaceData(it)
            startSwitchJob()
        }
    }

    override fun getBannerFailed(str : String) {
        onShowToast(str)
    }


    override fun getListSuccess(result: HomeListResponse) {
        result.data.datas.let {
            homeAdapter.run {
                //列表总数
                val total = result.data.total
                //当前总数 >  列表总数
                if(result.data.offset >= total || data.size >= total){
                    loadMoreEnd() //结束，不在有数据
                    return@let
                }
                //判断是第一次加载数据，还是刷新
                if(swipe_refresh.isRefreshing){
                    replaceData(it)
                }else{
                    addData(it)
                }
                loadMoreComplete()//加载完成
                setEnableLoadMore(true)//设置启用的负载状态
            }
        }
        swipe_refresh.isRefreshing = false
    }

    override fun getListFailed(str: String?) {
        homeAdapter.setEnableLoadMore(false)
        homeAdapter.loadMoreFail()
        str?.let {
            onShowToast(it)
        } ?: let {
            onShowToast(getString(R.string.get_data_error))
        }
        swipe_refresh.isRefreshing = false
    }

    override fun getListNull() {
        onShowToast(getString(R.string.get_data_zero))
        swipe_refresh.isRefreshing = false
    }

    override fun getListSmall(result: HomeListResponse) {
        result.data.datas.let {
            homeAdapter.run {
                replaceData(it)
                loadMoreComplete()
                loadMoreEnd()
                setEnableLoadMore(false)
            }
        }
        swipe_refresh.isRefreshing = false
    }

    override fun collectArcitleSuccess(result: HomeListResponse, isAdd: Boolean) {
        onShowToast(if (isAdd) getString(R.string.bookmark_success) else getString(R.string.bookmark_cancel_success))
    }

    override fun collectArcitleFailed(errorMessage: String?, isAdd: Boolean) {
        onShowToast(if (isAdd) activity.getString(R.string.bookmark_failed, errorMessage) else activity.getString(R.string.bookmark_cancel_failed, errorMessage))
    }


    override fun onShowToast(str: String) {
        ToastUtils.toast(activity,str)
    }

    override fun onShowSuccess() {
        onShowToast("获取数据成功")
    }

    override fun onShowError() {
        onShowToast("获取数据失败")
    }

    private val myOnRefreshListener = SwipeRefreshLayout.OnRefreshListener{
        refreshData()
    }

    /**
     *加载更多监听
     */
    private val myRequestLoadMoreListener = BaseQuickAdapter.RequestLoadMoreListener{
        val page = homeAdapter.data.size / 20 + 1 //下一页的数据
        homeFragmentPresenter.getListResult(page)
    }

    /**
     * recyclerview条目点击事件
     */
    private val onItemClickListener = BaseQuickAdapter.OnItemClickListener{
        _, _, position ->
            if(lists.size != 0){ //判断有条目才可以点击
                Intent(activity,HomeContentActivity::class.java).run {
                    putExtra(Constant.CONTENT_URL_KEY , lists[position].link)//web界面显示的内容网址
                    putExtra(Constant.CONTENT_ID_KEY,lists[position].id)
                    putExtra(Constant.CONTENT_TITLE_KEY,lists[position].title)
                    startActivity(this)
                }
            }
    }
    /**
     * recyclerview条目中的控件的点击事件
     */
    private val onItemChildClickListener = OnItemChildClickListener{
        _, view, position ->
            if(lists.size != 0) { //判断有条目才可以点击
                val datas = lists[position]
                when(view.id){
                    R.id.tv_home_item_type ->{
                        /*datas.chapterName ?: let {
                            onShowToast(getString(R.string.type_null))
                            return@OnItemChildClickListener
                        }*/

                        Intent(activity,TypeContentActivity::class.java).run {
                            putExtra(Constant.CONTENT_TARGET_KEY,true)
                            putExtra(Constant.CONTENT_TITLE_KEY,datas.title)
                            putExtra(Constant.CONTENT_CID_KEY,datas.chapterId)
                            startActivity(this)
                        }

                    }
                    R.id.iv_home_item_like ->{
                        if(isLogin){//已经登录
                            val collect = datas.collect //判断是否已经收藏
                            datas.collect = !collect//如果已经收藏，就将Boolean值设置为false，反之一样
                            homeAdapter.setData(position, datas)
                            homeFragmentPresenter.collectArcitle(datas.id,collect)
                        }else{//未登录
                            //跳转到登录界面
                            Intent(activity,LoginActivity::class.java).run {
                                startActivityForResult(this,Constant.MAIN_REQUEST_CODE)
                            }
                            onShowToast(getString(R.string.not_login))

                        }
                    }
                }
        }

    }

    /**
     *banner点击事件
     */
    private val bannerItemClickListener = BaseQuickAdapter.OnItemClickListener{
        _, _, position ->
        if(bannerList.size != 0){
            Intent(activity,HomeContentActivity::class.java).run {
                putExtra(Constant.CONTENT_URL_KEY , bannerList[position].url)//web界面显示的内容网址
                putExtra(Constant.CONTENT_TITLE_KEY,bannerList[position].title)
                startActivity(this)
            }
        }
    }

    /**
     *banner的触摸事件
     */
    private val onTouchListener = View.OnTouchListener{
        _, event ->
        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                cancelSwitchJob()
            }
        }
        false
    }

    /**
     * banner的滚动事件
     */
    private val onScrollListener = object : RecyclerView.OnScrollListener() {
        //滚动转态发生变化
        override fun onScrollStateChanged(recyclerView: RecyclerView?, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            when(newState){
                RecyclerView.SCROLL_STATE_IDLE ->{//空闲状态
                    currentIndex = linearLayoutManager.findFirstVisibleItemPosition() //找到第一个可见的项目位置
                    println("currentIndex = $currentIndex")
                    startSwitchJob()
                }
            }
        }
    }

    /**
     *滚动到顶部
     */
    fun smoothScrollToPosition() = recycle_view_home.scrollToPosition(0)

    /*刷新数据*/
     fun refreshData() {
        swipe_refresh.isRefreshing = true
        homeAdapter.setEnableLoadMore(false)
        cancelSwitchJob()
        homeFragmentPresenter.getBannerResult()
        homeFragmentPresenter.getListResult()
    }

    /**
     *轮播
     */
    private fun getBannerSwitchJob() = launch {
        repeat (Int.MAX_VALUE){ //循环
            if(bannerList.size ==0){//没有数据，直接跳出
                return@launch
            }
            delay(BANNER_TIME) //设置banner的滚动的时间间隔
            currentIndex++
            val index = currentIndex % bannerList.size //取余
            bannerRecyclerView.smoothScrollToPosition(index)//平滑滚动到指定位置
            currentIndex = index
        }
    }

    /**
     *开始轮播
     */
    private fun startSwitchJob() = bannerSwitchJob?.run {
        if (!isActive) {
            bannerSwitchJob = getBannerSwitchJob().apply { start() }
        }
    } ?: let {
        bannerSwitchJob = getBannerSwitchJob().apply { start() }
    }

    /**取消轮播*/
    private fun cancelSwitchJob() = bannerSwitchJob?.run {
        if (isActive) {
            cancel()
        }
    }
}