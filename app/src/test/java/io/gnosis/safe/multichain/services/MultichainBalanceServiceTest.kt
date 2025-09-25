package io.gnosis.safe.multichain.services

import io.gnosis.data.models.Chain
import io.gnosis.data.models.Safe
import io.gnosis.data.models.assets.Balance
import io.gnosis.data.models.assets.CoinBalances
import io.gnosis.data.models.assets.TokenInfo
import io.gnosis.data.repositories.TokenRepository
import io.gnosis.safe.multichain.models.MultichainSafe
import io.gnosis.safe.ui.base.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import pm.gnosis.model.Solidity
import pm.gnosis.utils.asEthereumAddress
import java.math.BigDecimal
import java.math.BigInteger

class MultichainBalanceServiceTest {

    @Mock
    private lateinit var tokenRepository: TokenRepository

    private lateinit var multichainBalanceService: MultichainBalanceService
    private lateinit var appDispatchers: AppDispatchers

    private val testAddress = "0x1234567890123456789012345678901234567890".asEthereumAddress()!!

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        appDispatchers = AppDispatchers(Dispatchers.Unconfined, Dispatchers.Unconfined)
        multichainBalanceService = MultichainBalanceService(tokenRepository, appDispatchers)
    }

    @Test
    fun `loadAggregatedBalances aggregates balances across multiple chains correctly`() = runTest {
        // Given
        val chain1 = createTestChain(BigInteger.ONE, "Ethereum")
        val chain2 = createTestChain(BigInteger.valueOf(137), "Polygon")
        
        val safe1 = createTestSafe(testAddress, "My Safe", BigInteger.ONE, chain1)
        val safe2 = createTestSafe(testAddress, "My Safe", BigInteger.valueOf(137), chain2)
        
        val multichainSafe = MultichainSafe(
            address = testAddress,
            localName = "My Safe",
            safesPerChain = mapOf(
                BigInteger.ONE to safe1,
                BigInteger.valueOf(137) to safe2
            )
        )

        // Mock balance responses
        val ethBalance = createTestCoinBalances(BigDecimal("100.50"))
        val polygonBalance = createTestCoinBalances(BigDecimal("75.25"))
        
        `when`(tokenRepository.loadBalanceOf(safe1, "USD")).thenReturn(ethBalance)
        `when`(tokenRepository.loadBalanceOf(safe2, "USD")).thenReturn(polygonBalance)

        // When
        val result = multichainBalanceService.loadAggregatedBalances(multichainSafe, "USD")

        // Then
        assertEquals(BigDecimal("175.75"), result.totalFiatValue)
        assertEquals(2, result.balancesByChain.size)
        assertTrue(result.balancesByChain.containsKey(chain1))
        assertTrue(result.balancesByChain.containsKey(chain2))
        assertFalse(result.hasErrors)
    }

    @Test
    fun `loadAggregatedBalances handles partial failures gracefully`() = runTest {
        // Given
        val chain1 = createTestChain(BigInteger.ONE, "Ethereum")
        val chain2 = createTestChain(BigInteger.valueOf(137), "Polygon")
        
        val safe1 = createTestSafe(testAddress, "My Safe", BigInteger.ONE, chain1)
        val safe2 = createTestSafe(testAddress, "My Safe", BigInteger.valueOf(137), chain2)
        
        val multichainSafe = MultichainSafe(
            address = testAddress,
            localName = "My Safe",
            safesPerChain = mapOf(
                BigInteger.ONE to safe1,
                BigInteger.valueOf(137) to safe2
            )
        )

        // Mock one success, one failure
        val ethBalance = createTestCoinBalances(BigDecimal("100.50"))
        `when`(tokenRepository.loadBalanceOf(safe1, "USD")).thenReturn(ethBalance)
        `when`(tokenRepository.loadBalanceOf(safe2, "USD")).thenThrow(RuntimeException("Network error"))

        // When
        val result = multichainBalanceService.loadAggregatedBalances(multichainSafe, "USD")

        // Then
        assertEquals(BigDecimal("100.50"), result.totalFiatValue) // Only successful chain
        assertEquals(1, result.balancesByChain.size) // Only Ethereum
        assertEquals(1, result.errors.size) // Polygon failed
        assertTrue(result.hasErrors)
        assertTrue(result.isPartiallyLoaded)
    }

    private fun createTestChain(chainId: BigInteger, name: String): Chain {
        return Chain(
            chainId = chainId,
            l2 = false,
            name = name,
            shortName = name.substring(0, minOf(3, name.length)).uppercase(),
            textColor = "#FFFFFF",
            backgroundColor = "#FF0000",
            rpcUri = "https://ethereum.rpc.url",
            rpcAuthentication = Chain.RpcAuthentication.NO_AUTHENTICATION,
            blockExplorerTemplateAddress = "https://etherscan.io/address/{{address}}",
            blockExplorerTemplateTxHash = "https://etherscan.io/tx/{{txHash}}",
            ensRegistryAddress = null,
            features = emptyList()
        ).apply {
            currency = Chain.Currency.DEFAULT_CURRENCY
        }
    }

    private fun createTestSafe(
        address: Solidity.Address,
        name: String,
        chainId: BigInteger,
        chain: Chain
    ): Safe {
        return Safe(
            address = address,
            localName = name,
            chainId = chainId
        ).apply {
            this.chain = chain
        }
    }

    private fun createTestCoinBalances(fiatTotal: BigDecimal): CoinBalances {
        val tokenInfo = TokenInfo(
            address = testAddress,
            decimals = 18,
            symbol = "ETH",
            name = "Ethereum",
            logoUri = null
        )
        
        val balance = Balance(
            tokenInfo = tokenInfo,
            balance = BigInteger("1000000000000000000"), // 1 ETH
            fiatBalance = fiatTotal,
            fiatConversion = BigDecimal.ONE
        )
        
        return CoinBalances(
            fiatTotal = fiatTotal,
            items = listOf(balance)
        )
    }
}
